package com.lexvay.tenant;

/**
 * Whether a tenant holds fictional demonstration data or real data (D-560). It is stored on the tenant and changed
 * only by a database administrator. A DEMO tenant may use the scripted model and everything it produces says it is
 * fictional; a LIVE tenant never uses the scripted model.
 */
public enum DataMode {
    DEMO, LIVE;

    /** The line that marks every report, export and package of a DEMO tenant. */
    public static final String DEMO_NOTICE = "Fictional demo data";
}
