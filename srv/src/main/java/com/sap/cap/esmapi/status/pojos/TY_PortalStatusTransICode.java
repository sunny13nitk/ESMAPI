package com.sap.cap.esmapi.status.pojos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TY_PortalStatusTransICode
{
    private TY_PortalStatusTransI transCfg;
    private String toStatusCode;
}
