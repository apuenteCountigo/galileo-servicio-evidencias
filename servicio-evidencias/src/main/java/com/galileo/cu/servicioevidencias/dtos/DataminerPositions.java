package com.galileo.cu.servicioevidencias.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

@Value
@Data
@Getter
@Setter
@AllArgsConstructor
public class DataminerPositions {
    private String Page;
    private int PageWeight;
    private int Row;
    private int Column;
}
