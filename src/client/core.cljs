(ns client.core
  (:require
   [reagent.dom :as rdom]
   [client.ui :as ui]))

(defn render! []
  (rdom/render
   [ui/app] ;; top level component
   (js/document.getElementById "app")))

(defn reload! []
  (render!))

(defn init []
  (ui/init)
  (render!))

