package com.github.violectra.ideaplugin.services

import com.github.violectra.ideaplugin.*
import com.github.violectra.ideaplugin.listeners.MyPsiTreeChangeListener
import com.github.violectra.ideaplugin.listeners.ReloadTreeListener
import com.github.violectra.ideaplugin.model.*
import com.github.violectra.ideaplugin.utils.MyNodeUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.xml.*
import com.intellij.util.LocalFileUrl
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomManager
import io.ktor.http.*
import javax.swing.tree.DefaultMutableTreeNode


@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) : Disposable {

    var treeRoot: DefaultMutableTreeNode? = null
    private lateinit var rootFile: PsiFile
    private val treeNodesByDomNodes = HashMap<MyNode, DefaultMutableTreeNode>()

    init {
        thisLogger().info(MyBundle.message("projectService", project.name))
        PsiManager.getInstance(project).addPsiTreeChangeListener(MyPsiTreeChangeListener(project), this)
    }

    fun reloadTreeForNewFile(file: VirtualFile) {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
        reloadTreeForFile(psiFile)
    }

    fun clearTree() {
        treeNodesByDomNodes.clear()
        reloadTreeWithNewRoot(null)
    }

    fun insertPsiElement(currentNode: Any, targetNode: Any) {
        val movableNode: DomElement = MyNodeUtils.getMovableNode(currentNode as MyNode)
        val target = targetNode as DomElement
        WriteCommandAction.runWriteCommandAction(project) {
            val copy = movableNode.xmlElement?.copy() ?: return@runWriteCommandAction
            movableNode.xmlElement?.delete()
            target.xmlElement?.add(copy)
        }
    }

    fun reloadTree() {
        project.messageBus.syncPublisher(ReloadTreeListener.RELOAD_MY_TREE_TOPIC)
            .reloadTree()
    }

    fun reloadAffectedSubTree(element: PsiElement) {
        if (element is XmlDocument || rootFile != element.containingFile) {
            reloadTreeForCurrentFile()
            return
        }

        val affectedNode = getAffectedNode(element)
        if (affectedNode == null) {
            reloadTree()
            return
        }
        if (affectedNode.xmlElement is XmlDocument || affectedNode.parent.xmlElement is XmlFile) {
            reloadTreeForCurrentFile()
            return
        }

        val affectedTreeNode = getTreeNode(affectedNode)
        val newNode = convertToTreeNodes(affectedNode)
        if (affectedTreeNode != null && affectedTreeNode.parent != null) {
            project.messageBus.syncPublisher(ReloadTreeListener.RELOAD_MY_TREE_TOPIC)
                .substituteTreeNode(affectedTreeNode, newNode)
        } else {
            val parentTreeNode = getTreeNode(affectedNode.parent)
                ?: throw RuntimeException("Parent node not found")
            val index = (affectedNode.parent as MyNodeWithChildren).getSubNodes()
                .indexOf(affectedNode as MyNodeWithIdAttribute)
            project.messageBus.syncPublisher(ReloadTreeListener.RELOAD_MY_TREE_TOPIC)
                .addTreeNode(parentTreeNode, newNode, index)
        }
    }

    private fun reloadTreeForCurrentFile() {
        reloadTreeForFile(rootFile)
    }

    private fun reloadTreeForFile(file: PsiFile) {
        rootFile = file
        treeNodesByDomNodes.clear()
        reloadTreeWithNewRoot(readDomStructureTreeNode(file))
    }

    private fun reloadTreeWithNewRoot(root: DefaultMutableTreeNode?) {
        treeRoot = root
        project.messageBus.syncPublisher(ReloadTreeListener.RELOAD_MY_TREE_TOPIC).handleTreeReloading(root)
    }

    private fun readDomStructureTreeNode(file: PsiFile): DefaultMutableTreeNode? {
        val parentFilePath = file.virtualFile.parent.url
        return getDomRoot(file)
            ?.let { convertToTreeNode(it, parentFilePath, setOf(file.name)) }
    }

    private fun findPsiFileByUrl(url: String): PsiFile? {
        return VirtualFileManager.getInstance().findFileByUrl(url)
            ?.let { PsiManager.getInstance(project).findFile(it) }
    }

    private fun getDomRoot(
        file: PsiFile,
    ) = if (file is XmlFile) {
        val fileElement = DomManager.getDomManager(project).getFileElement(file, Root::class.java)
        if (fileElement != null && fileElement.isValid) fileElement.rootElement else null
    } else null

    private fun convertToTreeNode(
        node: MyNode,
        parentFileUrl: String,
        usedSrc: Set<String>,
    ): DefaultMutableTreeNode {

        val newNode = if (node is NodeRef) {
            node.getSrc().value
                ?.let { LocalFileUrl(parentFileUrl).resolve(it).toString() }
                ?.let { findPsiFileByUrl(it) }
                ?.takeIf { it.name !in usedSrc }
                ?.let { getDomRoot(it) }
                ?.let { root -> RootWithExternalRef(root, node) } ?: node
        } else {
            node
        }
        val allowsChildren = newNode !is NodeRef
        val treeNode = DefaultMutableTreeNode(newNode, allowsChildren)
        val updatedUsedSrc = if (newNode is RootWithExternalRef) {
            newNode.nodeRef.getSrc().value?.let { usedSrc + it } ?: usedSrc
        } else usedSrc
        if (newNode is MyNodeWithChildren) {
            for (child in newNode.getSubNodes()) {
                treeNode.add(convertToTreeNode(child, parentFileUrl, updatedUsedSrc))
            }
        }
        treeNodesByDomNodes[node] = treeNode
        treeNodesByDomNodes[newNode] = treeNode
        return treeNode
    }

    private fun convertToTreeNodes(child: DomElement): DefaultMutableTreeNode {
        val node = child as MyNode
        val userSrc = calculateUsedSrcForNode(node)
        val parentUrl = rootFile.virtualFile.parent.url
        return convertToTreeNode(node, parentUrl, userSrc)
    }

    private fun calculateUsedSrcForNode(child: MyNode): Set<String> {
        val userSrc = mutableSetOf(rootFile.virtualFile.name)
        var cur: MyNode? = child
        while (cur != null) {
            if (cur is RootWithExternalRef) {
                cur.nodeRef.getSrc().value?.let { userSrc.add(it) }
            }
            cur = cur.parent as? MyNode
        }
        return userSrc
    }

    private fun getTreeNode(p: DomElement): DefaultMutableTreeNode? {
        return treeNodesByDomNodes[p as MyNode]
    }

    private fun getAffectedNode(element: PsiElement?): DomElement? {
        var cur: PsiElement? = element ?: return null
        while (cur != null) {
            if (cur is XmlTag) {
                val tag = DomManager.getDomManager(project).getDomElement(cur)
                if (tag != null) {
                    return tag
                }
            }
            cur = cur.parent
        }
        return null
    }

    override fun dispose() {
    }
}
