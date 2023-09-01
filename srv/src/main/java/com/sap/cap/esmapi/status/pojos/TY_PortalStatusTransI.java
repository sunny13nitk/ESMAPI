package com.sap.cap.esmapi.status.pojos;

import com.opencsv.bean.CsvBindByPosition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TY_PortalStatusTransI
{
    @CsvBindByPosition(position = 0)
    private String fromStatus;
    @CsvBindByPosition(position = 1)
    private String toStatus;
    @CsvBindByPosition(position = 2)
    private Boolean editAllowed;
}
