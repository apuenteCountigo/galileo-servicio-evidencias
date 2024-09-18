package com.galileo.cu.servicioevidencias.dtos;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TreeNode {
    public String title;
    public String key;
    public List<TreeNode> children;
    public boolean isLeaf;
}
