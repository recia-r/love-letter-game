(ns client.ui
  (:require
   [client.state :as state]
   [clojure.string :as str]
   [client.game :as game]
   [client.home :as home]
   [reagent.core :as r]))

(defn init [] 
  (state/init-routing!))

(defn set-user-name! [user-name]
  (-> (js/fetch "/api/user/set-name" #js {:method "POST"
                                          :body (doto (js/FormData.)
                                                  (.append "new-user-name" user-name))})
      (.then (fn [_response]
               (reset! state/player-name user-name)))))

(defn set-name-component []
  (r/with-let [username-input (r/atom "")]
    [:div {:style {:margin-bottom "15px"}}
     [:div {:style {:display "flex"
                    :align-items "center"
                    :justify-content "center"
                    :gap "8px"}}
      [:input {:type "text"
               :placeholder "Enter username"
               :value @username-input
               :on-change #(reset! username-input (-> % .-target .-value))
               :style {:padding "8px 12px"
                       :font-size "14px"
                       :border "1px solid #ccc"
                       :border-radius "4px"
                       :width "200px"}}]
      [:button {:on-click #(when (not (str/blank? @username-input))
                             (set-user-name! @username-input))
                :disabled (str/blank? @username-input)
                :style {:padding "8px 16px"
                        :background-color (if (str/blank? @username-input) "#ccc" "#4CAF50")
                        :color "white"
                        :border "none"
                        :border-radius "4px"
                        :cursor (if (str/blank? @username-input) "not-allowed" "pointer")
                        :font-size "14px"}}
       "Set Name"]]]))

(defn app []
  (if @state/player-name
    [:div
     [:div {:style {:padding "8px 12px"
                    :font-size "14px"
                    :font-weight "bold"
                    :color "#333"}}
      (str "Username: " @state/player-name)]
     (case (first @state/page)
       :page/game [game/game-page @state/page]
       :page/home [home/home-page])] 
    [set-name-component]))

