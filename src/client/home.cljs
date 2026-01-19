(ns client.home
  (:require
   [reagent.core :as r]
   [clojure.string :as str]
   [cljs.reader :as reader]
   [client.state :as state]))

(defn set-user-name! [user-name]
  (-> (js/fetch "/api/user/set-name" #js {:method "POST"
                                          :body (doto (js/FormData.)
                                                  (.append "user-name" user-name))})
      (.then (fn [_response]
               (reset! state/player-name user-name)))))

(defn create-room []
  (-> (js/fetch "/api/rooms/create" #js {:method "POST"})
      (.then (fn [response] (.text response)))
      (.then (fn [edn-text]
               (reset! state/rooms (reader/read-string edn-text))))))

(defn start-room-game [room-id]
  (-> (js/fetch (str "/api/rooms/start-game?room-id=" room-id) #js {:method "POST"})
      (.then (fn [response] (.text response)))
      (.then (fn [_edn-text]
               (reset! state/page [:page/game {:room-id room-id}])))))

(defn join-room-api [room-id]
  (-> (js/fetch (str "/api/rooms/join?room-id=" room-id) #js {:method "POST"})
      (.then (fn [response] (.text response)))
      (.then (fn [edn-text]
               (reset! state/rooms (reader/read-string edn-text))))))

(defn room-component [room]
  [:div.room-item
   {:style {:margin "10px 0"
            :padding "15px"
            :border "1px solid #ddd"
            :border-radius "4px"
            :background-color "#f9f9f9"}}
   [:p [:strong "Room ID: "] (subs (str (:room/id room)) 0 8)]
   [:p [:strong "Players: "] (str/join ", " (:room/players room))]
   [:p [:strong "State: "] (:room/state room)]
   [:button {:on-click (fn []
                         (join-room-api (:room/id room)))
             :disabled (contains? (:room/players room) @state/player-name)
             :style {:padding "8px 16px"
                     :background-color (if (contains? (:room/players room) @state/player-name)
                                         "#ccc"
                                         "#FF9800")
                     :color "white"
                     :border "none"
                     :border-radius "4px"
                     :margin-top "10px"
                     :margin-right "10px"}}
    "Join Room"]
   [:button {:on-click (fn []
                         (if (= (:room/state room) :in-game)
                           (reset! state/page [:page/game {:room-id (:room/id room)}])
                           (start-room-game (:room/id room))))
             :disabled (<= (count (:room/players room)) 1)
             :style {:padding "8px 16px"
                     :background-color (if (> (count (:room/players room)) 1)
                                         "#4CAF50"
                                         "#ccc")
                     :color "white"
                     :border "none"
                     :border-radius "4px"
                     :margin-top "10px"}}
    "Enter Game"]])

(defn joinable-rooms-component []
  (r/with-let
    [joinable-rooms (state/fetch-atom {:url "/api/rooms/joinable"})]
    (when (seq @joinable-rooms)
      [:div.joinable-rooms
       {:style {:margin-top "30px" :text-align "center"}}
       [:h3 "Rooms I Can Join"]
       (for [room @joinable-rooms]
         ^{:key (:room/id room)}
         [room-component room])])))

(defn player-rooms-component []
  (r/with-let
    [player-rooms (state/fetch-atom {:url "/api/rooms/player"})]
    (when (seq @player-rooms)
      [:div.joinable-rooms
       {:style {:margin-top "30px" :text-align "center"}}
       [:h3 "Rooms I'm In"]
       (for [room @player-rooms]
         ^{:key (:room/id room)}
         [room-component room])])))

(defn home-page []
  (let [username-input (r/atom "")]
    (fn []
      [:<>
       [:div.actions
        {:style {:margin-top "30px" :text-align "center"}}
        [:div {:style {:margin-bottom "15px"}}
         (if @state/player-name
           ;; Username already set in cookie - display as non-editable text
           [:div {:style {:padding "8px 12px"
                          :font-size "14px"
                          :font-weight "bold"
                          :color "#333"}}
            (str "Username: " @state/player-name)]
           ;; Username not set - show editable input with submit button
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
             "Set Name"]])]
        [:button {:on-click #(create-room)
                  :style {:padding "10px 20px"
                          :background-color "#2196F3"
                          :color "white"
                          :border "none"
                          :border-radius "4px"
                          :cursor "pointer"
                          :font-size "16px"}}
         "Create Room"]]
       [player-rooms-component]
       [joinable-rooms-component]])))

