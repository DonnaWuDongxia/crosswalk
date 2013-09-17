// sync implementation
var msg = extension.internal.sendSyncMessage(JSON.stringify({"cmd":"getDeviceInfo"}));
try {
    msg = JSON.parse(msg);
} catch (e) {
    throw("Device plugin: Invalid response for getDeviceInfo");
    throw(e);
}
if (msg.error) {
    throw("Device plugin: error response - " + msg.errorMessage);
} else if (msg.cmd === "getDeviceInfo") {
    var deviceInfo = msg.reply;
    exports.model = deviceInfo.model;
    exports.crosswalk = deviceInfo.crosswalk;
    exports.platform = deviceInfo.platform;
    exports.uuid = deviceInfo.uuid;
    exports.version = deviceInfo.version;
}
