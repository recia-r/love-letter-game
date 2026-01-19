(ns client.state
  (:require
   [reagent.core :as r]
   [clojure.string :as str]
   [cljs.reader :as reader]))

;; Global state atoms
(defonce rooms (r/atom nil))

(defonce page (r/atom [:page/home {}]))

(defn get-user-name []
  (let [cookie-name "dd-user-name="]
    (some->> (str/split (.-cookie js/document) #";\s*")
             (some #(when (str/starts-with? % cookie-name)
                      (subs % (count cookie-name)))))))

(defonce player-name (r/atom (get-user-name)))

;; Fetch utilities
(defn fetch [{:keys [url]}]
  (-> (js/fetch url)
      (.then (fn [response] (.text response)))
      (.then (fn [edn-text]
               (reader/read-string edn-text)))
      (.catch (fn [error]
                (js/console.error "Error fetching:" url ":" error)))))

(defn fetch-atom [{:keys [url]}]
  (let [a (r/atom nil)]
    (-> (fetch {:url url})
        (.then (fn [response]
                 (reset! a response))))
    a))

