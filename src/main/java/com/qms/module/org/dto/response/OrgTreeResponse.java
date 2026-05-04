package com.qms.module.org.dto.response;

import com.qms.module.org.enums.DepartmentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Top-down tree view of the entire organisation.
 *
 * Shape (intended for the front-end tree visualisation):
 *
 *   site
 *    └── siteHead (UserNode)
 *    └── departments[]
 *           ├── hod (UserNode)
 *           ├── members[] (UserNode)
 *           └── subDepartments[] (recursive)
 */
@Data
@Builder
public class OrgTreeResponse {

    private SiteNode site;

    @Data
    @Builder
    public static class SiteNode {
        private Long           id;
        private String         name;
        private String         code;
        private String         address;
        private UserNode       head;
        private List<DeptNode> departments;
    }

    @Data
    @Builder
    public static class DeptNode {
        private Long           id;
        private String         name;
        private String         code;
        private DepartmentType deptType;
        private UserNode       hod;
        private List<UserNode> members;
        private List<DeptNode> subDepartments;
        private Integer        totalMemberCount;   // includes nested subdepts
    }

    @Data
    @Builder
    public static class UserNode {
        private Long      id;
        private String    username;
        private String    fullName;
        private String    initials;
        private String    designation;
        private String    email;
        private String    phone;
        private LocalDate joiningDate;
        private Boolean   isHod;
        private Boolean   isDeptReviewer;
        private Boolean   isQaReviewer;
        private Boolean   hasActiveLicense;
    }
}
