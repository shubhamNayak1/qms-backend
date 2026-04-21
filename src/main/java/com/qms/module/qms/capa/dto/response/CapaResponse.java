package com.qms.module.qms.capa.dto.response;

import com.qms.module.qms.common.dto.response.QmsBaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CapaResponse extends QmsBaseResponse {

    private String    source;
    private String    capaType;
    private String    preventiveAction;
    private LocalDate effectivenessCheckDate;
    private String    effectivenessResult;
    private Boolean   isEffective;
    private String    linkedDeviationNumber;
}
