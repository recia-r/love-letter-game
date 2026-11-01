(ns client.ui
  (:require
   [client.state :as state]
   [clojure.string :as str]
   [client.game :as game]
   [client.home :as home]
   [reagent.core :as r]))

(defn init []
  (state/init-routing!)
  (state/fetch-user-name!))

(defn set-user-name! [user-name]
  (-> (js/fetch "/api/user/set-name" #js {:method "POST"
                                          :body (doto (js/FormData.)
                                                  (.append "new-user-name" user-name))})
      (.then (fn [_response]
               (reset! state/player-name user-name)))))

(def showcase-card-values [1 5 0 7 9])

(defn card-fan []
  (let [n (count showcase-card-values)
        middle (quot n 2)]
    [:div {:style {:display "flex"
                   :justify-content "center"
                   :margin "30px 0 70px 0"}}
     (for [[i value] (map-indexed vector showcase-card-values)
           :let [offset (- i middle)]]
       ^{:key value}
       [:img {:src (str "/card?value=" value)
              :alt (str "Card " value)
              :style {:width "130px"
                      :margin "0 -15px"
                      :border-radius "6px"
                      :box-shadow "0 4px 12px rgba(0,0,0,0.25)"
                      :transform (str "rotate(" (* offset 8) "deg)"
                                      " translateY(" (* (js/Math.abs offset) 12) "px)")}}])]))

(defn set-name-component []
  (r/with-let [username-input (r/atom "")]
    [:div {:style {:margin-bottom "15px"
                   :padding-top "30px"
                   :text-align "center"}}
     [:h1 {:style {:font-size "3em" :margin "0 0 10px 0"}} "Duck Dynasty"]
     [:p {:style {:color "#666" :margin "0"}}
      "Outwit your rivals and win the throne. Most rounds won out of 5 takes the game."]
     [card-fan]
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
       ;; key by room so moving to a replay room remounts the page (new polling, fresh state)
       :page/game ^{:key (:room-id (second @state/page))} [game/game-page @state/page]
       :page/home [home/home-page])] 
    [set-name-component]))

