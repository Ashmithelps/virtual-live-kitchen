package com.virtualkitchen.order;

/** Who is asking for a status change; {@link OrderStateMachine} decides what each may do. */
public enum OrderActor {
    CUSTOMER,
    OWNER,
    CHEF
}
