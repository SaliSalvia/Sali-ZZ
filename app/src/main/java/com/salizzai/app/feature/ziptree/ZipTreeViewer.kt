package com.salizzai.app.feature.ziptree

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salizzai.app.data.model.FileTreeNode
import com.salizzai.app.util.ZipExtractor

@Composable
fun ZipTreeViewer(
    nodes: List<FileTreeNode>,
    modifier: Modifier = Modifier
) {
    val expandedDirs = remember { mutableStateMapOf<String, Boolean>() }
    var expandAll by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ساختار فایل‌ها",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    expandAll = !expandAll
                    expandedDirs.clear()
                    if (expandAll) {
                        setAllExpanded(nodes, true) { k, v -> expandedDirs[k] = v }
                    }
                }) {
                    Text(if (expandAll) "جمع کردن همه" else "باز کردن همه")
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            Column {
                nodes.forEach { node ->
                    TreeNode(
                        node = node,
                        depth = 0,
                        expandedDirs = expandedDirs,
                        defaultExpandDepth = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeNode(
    node: FileTreeNode,
    depth: Int,
    expandedDirs: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    defaultExpandDepth: Int
) {
    val isDir = node.isDirectory
    val isExpanded = expandedDirs[node.path] ?: (depth < defaultExpandDepth)

    Column(modifier = Modifier.padding(start = (depth * 12).dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (isDir) expandedDirs[node.path] = !isExpanded }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDir) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(if (isExpanded) 90f else 0f)
                )
            } else {
                Spacer(modifier = Modifier.size(20.dp))
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = node.name + if (isDir) "/" else "",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isDir) FontWeight.Bold else FontWeight.Normal,
                color = if (isDir) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (node.size > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ZipExtractor.formatBytes(node.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isDir && isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                node.children.forEach { child ->
                    TreeNode(
                        node = child,
                        depth = depth + 1,
                        expandedDirs = expandedDirs,
                        defaultExpandDepth = defaultExpandDepth
                    )
                }
            }
        }
    }
}

private fun setAllExpanded(
    nodes: List<FileTreeNode>,
    expand: Boolean,
    setter: (String, Boolean) -> Unit
) {
    nodes.forEach { node ->
        if (node.isDirectory) {
            setter(node.path, expand)
            setAllExpanded(node.children, expand, setter)
        }
    }
}
