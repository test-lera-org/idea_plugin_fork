package com.github.violectra.ideaplugin.listeners

import com.intellij.util.messages.Topic
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeNode


interface ReloadTreeListener {
    companion object {
        val RELOAD_MY_TREE_TOPIC = Topic.create("RELOAD_MY_TREE", ReloadTreeListener::class.java)
    }

    fun handleTreeReloading(root: TreeNode?)
    fun substituteTreeNode(oldNode: MutableTreeNode, newNode: DefaultMutableTreeNode)
    fun addTreeNode(parent: MutableTreeNode, newNode: DefaultMutableTreeNode, index: Int)
    fun reloadTree()
}