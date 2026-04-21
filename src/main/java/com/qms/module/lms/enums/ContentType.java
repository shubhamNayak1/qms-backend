package com.qms.module.lms.enums;

/** Type of a content item inside a training program. */
public enum ContentType {
    DOCUMENT,       // link to a DMS-managed controlled document (read + acknowledge)
    VIDEO,          // external/internal video URL
    SLIDE_DECK,     // PowerPoint / PDF presented inline
    SCORM,          // SCORM 1.2 / 2004 e-learning package
    QUIZ,           // standalone inline knowledge-check quiz
    EXTERNAL_LINK,  // any URL requiring manual acknowledgement
    TEXT            // inline rich-text authored in the LMS editor
}
