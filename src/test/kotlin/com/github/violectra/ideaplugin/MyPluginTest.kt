package com.github.violectra.ideaplugin

import com.github.violectra.ideaplugin.model.*
import com.github.violectra.ideaplugin.services.MyProjectService
import com.intellij.openapi.components.service
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import javax.swing.tree.DefaultMutableTreeNode


@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    fun testTreeRootOnly() {
        val file = myFixture.createFile("test.xml", "<root></root>")
        myFixture.openFileInEditor(file)

        val service = myFixture.project.service<MyProjectService>()
        val treeRoot = service.treeRoot
        TestCase.assertNotNull("Root is null", treeRoot)
        TestCase.assertEquals("Root children should be empty", 0, treeRoot!!.childCount)
    }

    fun testTreeRootOnlyTypeNode() {
        val file = myFixture.configureByFile("test0.xml")
        myFixture.openFileInEditor(file.virtualFile)

        val service = myFixture.project.service<MyProjectService>()
        val treeRoot = service.treeRoot
        TestCase.assertNotNull(treeRoot)
        TestCase.assertEquals(0, treeRoot!!.childCount)

        myFixture.type("<nodeA id=\"new1\"></nodeA>")

        val treeRoot2 = service.treeRoot
        TestCase.assertNotNull(treeRoot2)
        TestCase.assertEquals(1, treeRoot2!!.childCount)
        val rootChildren = treeRoot2.children()
        val child1 = rootChildren.nextElement()
        TestCase.assertTrue(child1 is DefaultMutableTreeNode)
        val child1Node = (child1 as DefaultMutableTreeNode).userObject
        TestCase.assertTrue(child1Node is NodeA)
        TestCase.assertEquals("new1", (child1Node as NodeA).getId().value)
        TestCase.assertNull(child1Node.getTitle().value)
        TestCase.assertEquals(0, child1.childCount)
    }

    fun testTreeMultipleSimpleNestedNodes() {
        val file = myFixture.configureByFile("test1.xml")
        myFixture.openFileInEditor(file.virtualFile)
        val service = myFixture.project.service<MyProjectService>()
        val treeRoot = service.treeRoot
        TestCase.assertNotNull("Root is null", treeRoot)
        TestCase.assertEquals(2, treeRoot!!.childCount)
        val rootChildren = treeRoot.children()
        val child1 = rootChildren.nextElement()
        TestCase.assertTrue(child1 is DefaultMutableTreeNode)
        val child1Node = (child1 as DefaultMutableTreeNode).userObject
        TestCase.assertTrue(child1Node is NodeA)
        TestCase.assertEquals("id1", (child1Node as NodeA).getId().value)
        TestCase.assertEquals("title1", child1Node.getTitle().value)
        TestCase.assertEquals(1, child1.childCount)
        val child1Child = child1.children().nextElement()
        TestCase.assertTrue(child1Child is DefaultMutableTreeNode)
        val child1ChildNode = (child1Child as DefaultMutableTreeNode).userObject
        TestCase.assertTrue(child1ChildNode is NodeB)
        TestCase.assertEquals("id2", (child1ChildNode as NodeB).getId().value)
        TestCase.assertEquals("title2", child1ChildNode.getTitle().value)
        TestCase.assertEquals(0, child1Child.childCount)

        val child2 = rootChildren.nextElement()
        TestCase.assertTrue(child2 is DefaultMutableTreeNode)
        val child2Node = (child2 as DefaultMutableTreeNode).userObject
        TestCase.assertTrue(child2Node is NodeRef)
        TestCase.assertEquals("id3", (child2Node as NodeRef).getId().value)
        TestCase.assertEquals("title3", child2Node.getTitle().value)
        TestCase.assertEquals(0, child2.childCount)
    }

    fun testTreeMultipleNodeRefExtendedNestedNodes() {
        val file = myFixture.configureByFile("test2.xml")
        myFixture.configureByFile("test3.xml")
        myFixture.openFileInEditor(file.virtualFile)

        val service = myFixture.project.service<MyProjectService>()
        val treeRoot = service.treeRoot
        TestCase.assertNotNull("Root is null", treeRoot)
        TestCase.assertEquals(1, treeRoot!!.childCount)
        val rootChildren = treeRoot.children()

        val child1 = rootChildren.nextElement()
        TestCase.assertTrue(child1 is DefaultMutableTreeNode)
        val child1Node = (child1 as DefaultMutableTreeNode).userObject
        TestCase.assertTrue(child1Node is RootWithExternalRef)
        val nodeRef = (child1Node as RootWithExternalRef).nodeRef
        TestCase.assertEquals("id1", nodeRef.getId().value)
        TestCase.assertEquals("title1", nodeRef.getTitle().value)
        TestCase.assertEquals("test3.xml", nodeRef.getSrc().value)
        TestCase.assertEquals(1, child1.childCount)
        val childOfChild = child1.children().nextElement()
        val childOfChildNode = (childOfChild as DefaultMutableTreeNode).userObject
        TestCase.assertTrue(childOfChildNode is NodeRef)
        val nodeRef2 = (childOfChildNode as NodeRef)
        TestCase.assertEquals("id2", nodeRef2.getId().value)
        TestCase.assertEquals("title2", nodeRef2.getTitle().value)
        TestCase.assertEquals("test2.xml", nodeRef2.getSrc().value)
        TestCase.assertEquals(0, childOfChild.childCount)
    }

    override fun getTestDataPath() = "src/test/testData/treeBuilding"
}
