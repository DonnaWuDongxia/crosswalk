//This will be a native module, extension js stub can requireNative("jsStub") to get all the interfaces

/*
 * This is the js wrapper
 * Should be used as
 * requireNative("jsStub")
 */

/*
 * Serialize JavaScript object to JSON string and will be sent to Java. 
 * JavaScript --> JSON --> Java
 *
 * usage:
 * 1. serialize arguments for "invokeNativeMethod"
 * 2. serialize the value for "setProperty"
 * input: JS array
 * output: string including right slash
 * example:
 * args = ["Bob", 27, "Male"];
 * json: "[\"Bob\", 27, \"Male\"]";
 */
function jsToJson (obj) {
  return JSON.stringify(obj);
}

/*
 * Construct JavaScript object from JSON string received from Java. 
 * Java --> JSON --> JavaScript
 *
 * usage:construct the arguments for "invokeJsCallback"
 * 1. delete "@type" for ordinary object
 * 2. constructor the right js object, if the "@type" is exposed js constructor
 * input: JSON string
 * output: JavaScript object 
 * example:
 * json: "["Bob", 27, "Male", ]";
 * args = ["Bob", 27, "Male"];
 *
 * json: "{\"@type\":\"com.godoox.software.User\",\"name\":\"Bob\",\"age\":27,\"sex\":\"Male\"}"
 * args = {
 *   name:"Bob",
 *   age:27,
 *   sex:"Male"
 * }
 */
function jsonToJs (obj) {
  obj = JSON.parse(obj);
  if(obj instanceof Object) {
    if(obj.hasOwnProperty("@type")) {
      delete obj["@type"];
    }
  }
  return obj;
}

var jsStub = function(base, channel, isConstructor) {
  var nextCallbackId = 1;
  var instanceId = 1;

  //refer to the exposed extension object
  this.base = base;

  //refer to the global extension variable, used to send message
  this.channel = channel;
  this.isConstructor = !!(isConstructor);
  //retain the properties which is exposed by native
  this.properties = {};
  this.callbacks = [];

  this.getCallbackId = function() {
    while (this.callbacks[nextCallbackId] != undefined)
      ++nextCallbackId;
    return nextCallbackId;
  };

  if (this.isConstructor) {
    this.instances = [];
    //TODO: add this.constructor?
    this.getInstanceId = function() {
      while (this.instances[instanceId] != undefined)
        ++instanceId;
      return instanceId;
    };
  }
};

jsStub.create = function(base, channel, isConstructor) {
  var helper = jsStub.getHelper(base, channel, isConstructor);
  channel.setMessageListener(function (msg) {
    helper.handleMessage(msg);
  });
  return helper;
};

jsStub.getHelper = function(base, channel, isConstructor) {
  if (!(base.__stubHelper instanceof jsStub)) {
    base.__stubHelper = new jsStub(base, channel, isConstructor);
  }
  return base.__stubHelper;
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
  "invokeNative": function(name, args, sync) {
    debugger;
    if (!Array.isArray(args)) {
      console.warn("invokeNative: args is not an array.");
      args = Array(args);
    }

    // Retain callbacks in JS stub, replace them to related number ID
    var call = [];
    var cid = this.getCallbackId();
    args.forEach(function(val, vid, a) {
      if (!isSerializable(val)) {
        call[vid] = val;
        a[vid] = objectRef(cid, vid);
      }
    })

    if (call.length > 0) {
      this.callbacks[cid] = call;
    }
    
    var msg = {
      cmd: "invokeNative",
      name: name,
      args: args
    };
    if (sync)
      return this.sendSyncMessage(msg);
    else
      this.postMessage(msg);
  },
  "getNativeProperty": function(name) {
    return this.sendSyncMessage({
      cmd: "getProperty",
      name: name
    });
  },
  "setNativeProperty": function(name, value) {
    this.sendSyncMessage({
      cmd: "setProperty",
      name: name,
      value: value
    });
  },
  "newInstance": function(args) {
    this.postMessage({
      cmd: "newInstance",
      args: args
    });
  },
  "invokeCallback": function(id, key, args) {
      var cid = id >>> 8;
      var vid = id & 0xFF;
      var obj = this.callbacks[cid][vid];
      if (typeof(key) === 'number' || key instanceof Number)
          obj = obj[key];
      else if (typeof(key) === 'string' || key instanceof String)
          key.split('.').forEach(function(p){ obj = obj[p]; });

      if (obj instanceof Function)
          obj.apply(null, jsonToJs(args));
  },
  "handleMessage": function(json) {
    var msg = JSON.parse(json);
    if (!msg.cmd)
      console.warn("No valid Java CMD.");
    switch (msg.cmd) {
      case "invokeCallback":
        this.invokeCallback(msg.cid, msg.key, msg.args);
        break;
      case "updateProperty":
        // Case: property is changed by native and need
        // to sync its value to JS side.
        if(this.properties.hasOwnProperty(msg.name) >= 0) {
          this[msg.name] = this.getNativeProperty(msg.name);
        }
        break;
      case "error":
        // supported key: log, info, warn, error
        console[msg.level](msg.msg);
        break;
      case "dispatchEvent":
        if(msg.event.type) {
          this.base.dispatchEvent(msg.event);
        }
        break;
      default:
        console.warn("Unsupported Java CMD:" + msg.cmd);
    }
  },
  sendSyncMessage: function(msg) {
    debugger;
    var resultStr = this.channel.internal.sendSyncMessage(JSON.stringify(msg));

    //TODO: return null or undefined.
    console.log("invoke sync:" + JSON.stringify(resultStr));
    return resultStr.length > 0 ? JSON.parse(resultStr) : undefined;
  },

  postMessage: function(msg) {
    debugger;
    console.log("invoke PostMessage:" + JSON.stringify(msg));
    this.channel.postMessage(JSON.stringify(msg));
  },

  "destory": function() {}
};
 
// expose native property to JavaScript
jsStub.defineProperty = function(obj, prop, writable) {
  var helper = jsStub.getHelper(obj);
  var value = helper.getNativeProperty(prop);

  // keep all exported properties in the helper
  helper.properties[prop] = helper.getNativeProperty(prop);

  var desc = {
    'configurable': false,
    'enumerable': true,
    'get': function() { return helper.properties[prop]; }
  }
  if (writable) {
    desc.set = function(v) {
      helper.setNativeProperty(prop, v);
      helper.properties[prop] = v;
    }
  }
  Object.defineProperty(obj, prop, desc);
};

// EventTarget implementation:
// All extension instances will receive event data
jsStub.makeEventTarget = function(base) {
  var helper = jsStub.getHelper(base);
  helper._event_listeners = {};

  helper.addEvent = function(type, event) {
    Object.defineProperty(helper, "_on" + type, {
      writable : true,
    });

    Object.defineProperty(this, "on" + type, {
      get: function() {
        return helper["_on" + type];
      },
      set: function(listener) {
        var old_listener = helper["_on" + type];
        if (old_listener === listener)
          return;

        if (old_listener)
          base.removeEventListener(type, old_listener);

        helper["_on" + type] = listener;
        base.addEventListener(type, listener);
      },
      enumerable: true,
    });

  };

  function dispatchEvent(event) {
    if (!event.type)
      return;
    if (!(event.type in helper.event_listeners))
      return;

    var listeners = helper.event_listeners[event.type];
    for (var i in listeners)
      listeners[i](event);
  };

  function addEventListener(type, listener) {
    if (!(listener instanceof Function))
      return;

    if (!(("on" + type) in base))
      return;

    if (type in helper.event_listeners) {
      var listeners = helper.event_listeners[type];
      if (listeners.indexOf(listener) == -1)
        listeners.push(listener);
    } else {
      helper.event_listeners[type] = [listener];
    }
  };

  function removeEventListener(type, listener) {
    if (!(listener instanceof Function))
      return;

    if (!(type in helper.event_listeners))
      return;

    var listeners = helper.event_listeners[type];
    if(listeners.indexOf(listener) > -1)
      listeners.splice(index, 1);
  };

  Object.defineProperties(base, {
    "addEventListener" : {
      value : addEventListener,
      enumerable : true,
    },
    "removeEventListener" : {
      value : removeEventListener,
      enumerable : true,
    },
    "dispatchEvent" : {
      value : dispatchEvent,
      enumerable : true,
    },
  });
};

exports.jsStub = jsStub;
