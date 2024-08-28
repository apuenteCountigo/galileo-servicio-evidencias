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
public class DataminerParameter {
    private String __type;
    private int DataMinerID;
    private int ElementID;
    private String TableIndex;
    private String Value;
    private String DisplayValue;
    private String LastValueChange;
    private int LastValueChangeUTC;
    private String AlarmState;
    private String Filters;
    private int LastChangeUTC;
    private int ID;
    private String ParameterName;
    private String InfoSubText;
    private boolean IsMonitored;
    private boolean IsNumerical;
    private boolean HasRange;
    private int RangeLow;
    private int RangeHigh;
    private String RangeLowDisplay;
    private String RangeHighDisplay;
    private String Options;
    private String Unit;
    private int PrimaryKeyID;
    private int DisplayKeyID;
    private boolean IsLogarithmic;
    private boolean IsTableColumn;
    private boolean IsTable;
    private boolean IsTrending;
    private int TableParameterID;
    private Double Decimals;
    private String Type;
    private String WriteType;
    private String ReadType;
    private String Discreets;
    private String DashboardsType;
    private DataminerPositions[] Positions;
}
