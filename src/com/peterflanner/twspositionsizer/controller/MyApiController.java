package com.peterflanner.twspositionsizer.controller;

import com.ib.controller.ApiConnection;
import com.ib.controller.ApiController;

/**
 * Author: Pete
 * Date: 1/18/2018
 * Time: 6:10 AM
 */
public class MyApiController extends ApiController {
    private int orderId;
    
    public MyApiController(IConnectionHandler handler, ApiConnection.ILogger inLogger, ApiConnection.ILogger outLogger) {
        super(handler, inLogger, outLogger);
    }

    @Override public void nextValidId(int orderId) {
        super.nextValidId(orderId);
        this.orderId = orderId;
    }
    
    public int getNextValidId() {
        return orderId;
    }
}
