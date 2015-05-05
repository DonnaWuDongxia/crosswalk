//This will be a native module, extension js stub can requireNative("jsStub") to get all the interfaces

/*
 * This is the js wrapper
 * Should be used as
 * requireNative("jsStub")
 */
function AsyncCall(resolve, reject) {
  this.resolve = resolve;
  this.reject = reject;
}

function createPromise(msg) {
  var promise = new Promise(function(resolve, reject) {
    this.callbacks[nextCallbackId] = new AsyncCall(resolve, reject);
  }); 
  msg.callbackId = nextCallbackId;
  postMessage(msg);
  this.nextCallbackId++;
  return promise;
}

exports = jsStub = function (isConstructor) {
  var nextCallbackId = 1;
  var instanceId = 1;
  this.isConstructor = !!(insConstructor);
  this.callbacks = [];
  this.getCallbackId() {
    while (this.callbacks[nextCallbackId] != undefined)
      ++nextCallbackId;
    return nextCallbackId;
  };
  if (this.isConstructor) {
    this.instances = [];
    this.getInstanceId() {
      while (this.instances[instanceId] != undefined)
        ++instanceId;
      return instanceId;
    };
  }
};

jsStub.create = function(base, isConstructor) {

    /*TODO: do we need namespace?, c++ created the related object*/
    if (!isConstructor) {
        if (base instanceof Object)
            integrateAttributes(base, this);
        else
            base = new this();
    } else if (base instanceof Function) {
        base.lastInstanceID = 1;
        base.prototype = new this();
        base.prototype.constructor = base;
    }
    return base;
};

function integrateAttributs(base) {
    function clone(base) {
        var copy = {};
        var keys = Object.getOwnPropertyNames(base);
        for (var i in keys)
            copy[keys[i]] = base[keys[i]];
        return copy;
    }
    var p = clone(jsStub.prototype);
    p.__proto__ = Object.getPrototypeOf(base);
    base.__proto__ = p;
}

sendSyncMessage = function(msg) {                                                                                                                                                  
  resultStr = extension.internal.sendSyncMessage(JSON.stringify(msg));

  //TODO: return null or undefined.
  return resultStr.lenght > 0 ? JSON.parse(resultStr) : undefined;
};
 
postMessage = function(msg) {                                                                                                                                                  
  extension.postMessage(JSON.stringify(msg));
};

function isSerializable(obj) {
  if (!(obj instanceof Object))
    return true;
  if (obj instanceof Function)
    return false;
  if (obj instanceof Boolean ||
      obj instanceof Date ||
      obj instanceof Number ||
      obj instanceof RegExp ||
      obj instanceof String)
    return true;
  for (var p of Object.getOwnPropertyNames(obj))
    if (!isSerializable(obj[p]))
      return false;
  return true;
}   
  
function objectRef(cid, vid) {                                                                                                                                            
  return (cid << 8) + vid;
}   

jsStub.prototype = {
  "invokeNative": function(name, args) {
    if (Array.isArray(args)) {
      console.warn("invokeNative: args is not an array.");
      args = Array(args);
    }

    var call = [];
    args.forEach(function(val, vid, a) {                                                                                                                                            
      if (!isSerializable(val)) {
        call[vid] = val;
        a[vid] = objectRef(cid, vid);
      }
    })

    var cid = 0;
    if (call.length) {
      cid = this.getCallbackId();
      this.callbacks[cid] = call;
    }
    
    postMessage({
      cmd: "invokeMethod",
      name: name,
      args: args
    });
  },
  "getNativeProperty": function(name) {
    return sendSyncMessage({
      cmd: "getProperty",
      name: name
    });
  },
  "setNativeProperty": function(name, value) {
    postMessage({
      cmd: "setProperty",
      name: name,
      value: value
    });
  },
  "newInstance": function(args) {
    postMsg({
      cmd: "newInstance",
      value: value
    });
  },
  "invokeCallback": function() {},
  "destoryNative": function() {}
};
 
exports.setMessageListener(function(json){
   var msg = JSON.parse(json);
   if (msg.error) {
     return;
   }
   switch (msg.cmd) {
     case "invokeJsCallback":
       msg
       break;
     case "promiseResolve":
       msg.
       break;
     case "invokeJsMethod":
       break;
     case "setJsProperty":
       break;
     case "destoryJs":
       break;
     default:
   }
 });
 
exports.defineProperty = function defineProperty(obj, prop, value, isWritable) {
    if (!isWritable) {
        Object.defineProperty(object, prop, {
            "configurable": false,
            "enumerable": true,
            "writable": false,
            "value": value
        });
    } else {
        Object.defineProperty(object, prop, {
            "configurable": false,
            "enumerable": true,
            "get": function() {return getNativePropety(prop);},
            "set": function() {setNativeProperty(prop, value);};
        })
    }
}
