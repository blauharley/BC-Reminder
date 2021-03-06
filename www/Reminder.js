/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

var exec = require("cordova/exec");
module.exports = {

    /*
     @info
     start reminder

     @params

     successCallback: Function
     errorCallback: Function
     options:{
         mode: "aim"
         title : "Reminder-Notification"
         content: "Reminder-Content"
         interval: 60000 (in milliseconds)
         distance: 100 (in meters)
         aimCoord:{
            lat: 0,
            long: 0
         }
         whistle: true
         closeApp: true
         stopDate: "forever" ("forever" | "tomorrow")
         distanceTolerance: 10
         aggressive: true
     }
     */
    start : function (successCallback, errorCallback, options) {

        options = options || {};

        var title = options.title != undefined ? options.title : new Error('no title');
        var content = options.content != undefined ? options.content : new Error('no content');
        var interval = options.interval != undefined ? options.interval : new Error('no interval');
        var distance = options.distance != undefined ? options.distance : new Error('no distance');
        var whistle = options.whistle != undefined ? options.whistle : new Error('no whistle');
        var closeApp = options.closeApp != undefined ? options.closeApp : new Error('no closeApp');
        var stopDate = options.stopDate != undefined ? options.stopDate : new Error('no stopDate');

        var args = [title,content,interval,distance,whistle,closeApp,stopDate];

        exec(successCallback, errorCallback, "Reminder", "start", args);
    },

    /*
     @info
     stop reminder

     @params

     successCallback: Function
     errorCallback: Function
     */
    clear : function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, "Reminder", "clear", []);
    },

    /*
     @info
     request provider

     @params

     successCallback: Function
     @param coords:object
         @param accurancy:integer
         @param latitude:integer
         @param longitude:integer
         @param heading:float
         @param altitude:float
         @param speed:float
         @param gps_fix:boolean
         @param provider_enabled:boolean
         @param out_of_service:boolean
     @param timestamp:integer

     errorCallback: Function
     options:{
         interval: 60000 (in milliseconds)
     }
     */
    requestProvider : function (successCallback, errorCallback, options) {
        var interval = options.interval ? options.interval : 60000;
        exec(successCallback, errorCallback, "Reminder", "request", [interval]);
    },

    /*
     @info
     check reminder runs

     @params

     successCallback: Function
     errorCallback: Function
     */
    isRunning : function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, "Reminder", "isrunning", []);
    }

};