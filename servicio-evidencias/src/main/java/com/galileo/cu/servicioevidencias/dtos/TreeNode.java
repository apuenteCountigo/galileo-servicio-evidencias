package com.galileo.cu.servicioevidencias.dtos;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TreeNode {
    private String title;
    private String key;
    private List<TreeNode> children;
    private boolean isLeaf;
}
