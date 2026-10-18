use adblock::engine::Engine;
use adblock::lists::{FilterFormat, FilterSet};
use jni::objects::{JClass, JObjectArray, JString};
use jni::sys::{jboolean, jlong, jstring};
use jni::JNIEnv;
use std::sync::RwLock;

struct EngineContainer {
    engine: RwLock<Engine>,
}

#[no_mangle]
pub extern "system" fn Java_com_petal_browser_browser_adblock_BraveAdblockJni_nativeCreateEngine(
    mut env: JNIEnv,
    _class: JClass,
    rules: JObjectArray,
) -> jlong {
    let mut filter_set = FilterSet::new(false);
    let len = match env.get_array_length(&rules) {
        Ok(l) => l,
        Err(_) => return 0,
    };

    for i in 0..len {
        if let Ok(elem) = env.get_object_array_element(&rules, i) {
            let jstr: JString = elem.into();
            if let Ok(rule_str) = env.get_string(&jstr) {
                let s: String = rule_str.into();
                filter_set.add_filter_list(&s, FilterFormat::Standard);
            }
        }
    }

    let engine = Engine::from_filter_set(filter_set, true);
    let container = Box::new(EngineContainer {
        engine: RwLock::new(engine),
    });

    Box::into_raw(container) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_petal_browser_browser_adblock_BraveAdblockJni_nativeMatches(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    url: JString,
    host: JString,
    request_type: JString,
) -> jboolean {
    if engine_ptr == 0 {
        return 0;
    }

    let container = unsafe { &*(engine_ptr as *const EngineContainer) };
    let url_str: String = match env.get_string(&url) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let host_str: String = match env.get_string(&host) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let req_type_str: String = match env.get_string(&request_type) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let engine = match container.engine.read() {
        Ok(e) => e,
        Err(_) => return 0,
    };

    let blocker_result = engine.check_network_urls(&url_str, &host_str, &req_type_str);
    if blocker_result.matched {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_com_petal_browser_browser_adblock_BraveAdblockJni_nativeCosmeticCss(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    url: JString,
) -> jstring {
    if engine_ptr == 0 {
        return std::ptr::null_mut();
    }

    let container = unsafe { &*(engine_ptr as *const EngineContainer) };
    let url_str: String = match env.get_string(&url) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let engine = match container.engine.read() {
        Ok(e) => e,
        Err(_) => return std::ptr::null_mut(),
    };

    let cosmetic_resources = engine.url_cosmetic_resources(&url_str);
    let css = cosmetic_resources.hide_selectors.join(", ") + " { display: none !important; }";

    match env.new_string(css) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_petal_browser_browser_adblock_BraveAdblockJni_nativeDestroyEngine(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr != 0 {
        unsafe {
            let _ = Box::from_raw(engine_ptr as *mut EngineContainer);
        }
    }
}
