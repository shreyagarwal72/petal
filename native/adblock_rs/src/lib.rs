use wasm_bindgen::prelude::*;
use adblock::engine::Engine;
use adblock::lists::{FilterFormat, FilterSet};
use adblock::request::Request;
use serde::{Deserialize, Serialize};

#[wasm_bindgen]
pub struct AdblockEngine {
    engine: Engine,
}

#[derive(Serialize, Deserialize)]
pub struct JsBlockerResult {
    pub matched: bool,
    pub important: bool,
    pub redirect: Option<String>,
    pub rewritten_url: Option<String>,
    pub csp: Option<String>,
    pub filter: Option<String>,
}

#[derive(Serialize, Deserialize)]
pub struct JsCosmeticResources {
    pub hide_selectors: Vec<String>,
    pub exceptions: Vec<String>,
    pub injected_scriptlets: Vec<String>,
    pub generichide: bool,
}

#[wasm_bindgen]
impl AdblockEngine {
    #[wasm_bindgen(constructor)]
    pub fn new(rules: JsValue) -> Result<AdblockEngine, JsValue> {
        let rules_vec: Vec<String> = serde_wasm_bindgen::from_value(rules)
            .map_err(|e| JsValue::from_str(&e.to_string()))?;
        
        let mut filter_set = FilterSet::new(false);
        for rule_list in rules_vec {
            filter_set.add_filter_list(&rule_list, FilterFormat::Standard);
        }

        let engine = Engine::from_filter_set(filter_set, true);
        Ok(AdblockEngine { engine })
    }

    #[wasm_bindgen(js_name = fromSerialized)]
    pub fn from_serialized(bytes: &[u8]) -> Result<AdblockEngine, JsValue> {
        let mut engine = Engine::default();
        engine.deserialize(bytes)
            .map_err(|e| JsValue::from_str(&format!("Deserialization error: {:?}", e)))?;
        Ok(AdblockEngine { engine })
    }

    #[wasm_bindgen(js_name = serializeRaw)]
    pub fn serialize_raw(&self) -> Result<Vec<u8>, JsValue> {
        Ok(self.engine.serialize())
    }

    #[wasm_bindgen(js_name = checkNetworkRequest)]
    pub fn check_network_request(&self, url: &str, source_url: &str, request_type: &str) -> Result<JsValue, JsValue> {
        let req = Request::from_urls(url, source_url, request_type)
            .map_err(|e| JsValue::from_str(&format!("Invalid request params: {:?}", e)))?;
        
        let blocker_result = self.engine.check_network_request(&req);
        let res = JsBlockerResult {
            matched: blocker_result.matched,
            important: blocker_result.important,
            redirect: blocker_result.redirect,
            rewritten_url: blocker_result.rewritten_url,
            csp: blocker_result.csp,
            filter: blocker_result.filter,
        };
        serde_wasm_bindgen::to_value(&res)
            .map_err(|e| JsValue::from_str(&e.to_string()))
    }

    #[wasm_bindgen(js_name = urlCosmeticResources)]
    pub fn url_cosmetic_resources(&self, url: &str) -> Result<JsValue, JsValue> {
        let cosmetic_resources = self.engine.url_cosmetic_resources(url);
        let res = JsCosmeticResources {
            hide_selectors: cosmetic_resources.hide_selectors,
            exceptions: cosmetic_resources.exceptions,
            injected_scriptlets: cosmetic_resources.injected_scriptlets,
            generichide: cosmetic_resources.generichide,
        };
        serde_wasm_bindgen::to_value(&res)
            .map_err(|e| JsValue::from_str(&e.to_string()))
    }

    #[wasm_bindgen(js_name = hiddenClassIdSelectors)]
    pub fn hidden_class_id_selectors(&self, classes: JsValue, ids: JsValue, exceptions: JsValue) -> Result<JsValue, JsValue> {
        let classes_vec: Vec<String> = serde_wasm_bindgen::from_value(classes)
            .unwrap_or_default();
        let ids_vec: Vec<String> = serde_wasm_bindgen::from_value(ids)
            .unwrap_or_default();
        let exceptions_vec: Vec<String> = serde_wasm_bindgen::from_value(exceptions)
            .unwrap_or_default();
        
        let selectors = self.engine.hidden_class_id_selectors(&classes_vec, &ids_vec, &exceptions_vec);
        serde_wasm_bindgen::to_value(&selectors)
            .map_err(|e| JsValue::from_str(&e.to_string()))
    }

    #[wasm_bindgen(js_name = useResources)]
    pub fn use_resources(&mut self, resources: &str) -> Result<(), JsValue> {
        self.engine.use_resources(resources);
        Ok(())
    }
}
