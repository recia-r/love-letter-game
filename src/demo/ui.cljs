(ns demo.ui
  (:require
   [reagent.core :as r]
   [duck-dynasty.game :as dd]
   [clojure.string :as str]
   [cljs.reader :as reader]))

;; Game state atom
(defonce rooms (r/atom nil))

(defonce page (r/atom [:page/home {}]))

(defn get-user-name []
  (let [cookie-name "dd-user-name="]
    (some->> (str/split (.-cookie js/document) #";\s*")
             (some #(when (str/starts-with? % cookie-name)
                      (subs % (count cookie-name)))))))

(defonce player-name (r/atom (get-user-name)))

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


(defn init [])

(defn card-component [card]
  [:div.card
   {:style {:border "1px solid #ccc"
            :padding "10px"
            :border-radius "4px"
            :background-color "#f9f9f9"
            :display "flex"
            :flex-direction "column"
            :align-items "center"
            :width "200px"}}
   [:img {:src (str "/card?value=" (:card/value card))
          :alt (or (:card/name card) "Card")
          :style {:width "100%"
                  :height "auto"
                  :border-radius "4px"
                  :margin-bottom "10px"}}]])

(defn player-hand-component [state {:keys [draw-card!]} player-name]
  (let [hand (dd/player-hand state player-name)
        is-current-player? (= player-name (:state/current-player state))
        has-one-card? (= (count hand) 1)
        cards (for [card hand]
                ^{:key (str (:card/value card) "-" (:card/name card))}
                [card-component card])
        draw-button (when (and is-current-player? has-one-card?)
                      [:img {:key "draw-card"
                             :src "/card?value=back"
                             :alt "Draw Card"
                             :on-click #(draw-card!)
                             :style {:width "200px"
                                     :height "auto"
                                     :cursor "pointer"
                                     :border-radius "4px"
                                     :border "2px solid #4CAF50"
                                     :transition "transform 0.2s"}
                             :on-mouse-enter #(set! (-> % .-target .-style .-transform) "scale(1.05)")
                             :on-mouse-leave #(set! (-> % .-target .-style .-transform) "scale(1)")}])]
    [:div.player-hand
     {:style {:margin "10px 0"
              :max-width "none"}}
     (if (empty? hand)
       [:div "No cards (eliminated)"]
       [:div.cards
        {:style {:display "flex"
                 :flex-direction "row"
                 :flex-wrap "nowrap"
                 :align-items "center"
                 :gap "10px"}}
        (doall cards)
        draw-button])]))

;; TODO from Jeff - make smaller components e.g. for target and guess
;; do not pass state around - below should not get state as an argument, should get fns that alter state (state defined in top level component)
;; within each room, i'll have a game state. all components shuld get an immutable version of state (and fns should alter state)

(defn play-card-form [state {:keys [play-card!]} player-name card]
  (let [target-players (dd/targetable-players state (:card/value card))
        needs-target? (not (nil? (:card/targeting-rule card)))
        needs-guess? (= (:card/value card) 1) ; Minion needs a guess
        target (r/atom (first target-players))
        guessed-value (r/atom 2)]
    (fn []
      [:div.play-card-form
       {:style {:border "2px solid #4CAF50"
                :padding "15px"
                :margin "10px 0"
                :border-radius "4px"
                :background-color "#f0f8f0"}}
       [:div {:style {:font-weight "bold" :margin-bottom "10px"}}
        (str "Play " (:card/name card))]
       (when needs-target?
         [:div {:style {:margin-bottom "10px"}}
          [:label {:style {:display "block" :margin-bottom "5px"}} "Target Player:"]
          [:select {:value @target
                    :on-change #(reset! target (-> % .-target .-value))}
           (for [tp target-players]
             ^{:key tp}
             [:option {:value tp} tp])]])
       (when needs-guess?
         [:div {:style {:margin-bottom "10px"}}
          [:label {:style {:display "block" :margin-bottom "5px"}} "Guess Card Value:"]
          [:select {:value @guessed-value
                    :on-change #(reset! guessed-value (js/parseInt (-> % .-target .-value)))}
           (for [val [2 3 4 5 6 7 9 0]]
             ^{:key val}
             [:option {:value val} (str val " - " (:card/name (dd/card-by-value val)))])]])
       [:button {:on-click (fn []
                             (play-card!
                              (:card/value card)
                              (when needs-target? @target)
                              (when needs-guess? @guessed-value)))
                 :style {:padding "8px 16px"
                         :background-color "#4CAF50"
                         :color "white"
                         :border "none"
                         :border-radius "4px"
                         :cursor "pointer"}}
        "Play Card"]])))

(defn outer-player-component [state fns]
  (let [current-player (:state/current-player state)
        hand (dd/player-hand state current-player)
        is-current-player? (= @player-name current-player)]
    (when (not (dd/game-over? state))
      [:div.current-player
       {:style {:background-color "#e3f2fd"
                :padding "20px"
                :border-radius "8px"
                :margin "20px 0"}}
       [:h2 {:style {:margin-top "0"}}
        (str "Currently " current-player "'s turn")]
       [player-hand-component state fns @player-name]
       (when is-current-player?
         [:div.playable-cards
          {:style {:margin-top "20px"}}
          (for [card hand]
            ^{:key (str "play-" (:card/value card))}
            [:div {:style {:margin-bottom "15px"}}
             [play-card-form state fns current-player card]])])])))

(defn game-status-component [state _]
  [:div.game-status
   {:style {:background-color "#fff3cd"
            :padding "15px"
            :border-radius "8px"
            :margin "20px 0"}}
   (if (dd/game-over? state)
     (let [winners (dd/game-winners state)
           winner-text (str/join " and " winners)]
       [:div
        [:h2 {:style {:color "#856404"}} "Game Over!"]
        [:p {:style {:font-size "1.2em" :font-weight "bold"}}
         (str "Winner: " winner-text)]])
     [:div
      [:p (str "Active Players: " (str/join ", " (dd/active-players state)))]
      [:p (str "Round: " (:state/round state) " / " (:state/rounds state))]
      [:p (str "Remaining Cards: " (count (:state/deck state)))]])])



(defn set-user-name! [user-name]
  (-> (js/fetch "/api/user/set-name" #js {:method "POST"
                                          :body (doto (js/FormData.)
                                                  (.append "user-name" user-name))})
      (.then (fn [_response]
               (reset! player-name user-name)))
      (.catch (fn [error]
                (js/console.error "Error setting user name:" error)))))

(defn create-room []
  (-> (js/fetch "/api/rooms/create" #js {:method "POST"})
      (.then (fn [response] (.text response)))
      (.then (fn [edn-text]
               (reset! rooms (reader/read-string edn-text))))
      (.catch (fn [error]
                (js/console.error "Error creating room:" error)))))

(defn start-room-game [room-id]
  (-> (js/fetch (str "/api/rooms/start-game?room-id=" room-id) #js {:method "POST"})
      (.then (fn [response] (.text response)))
      (.then (fn [edn-text]
               (reset! page [:page/game {:room-id room-id}])))
      (.catch (fn [error]
                (js/console.error "Error starting room game:" error)))))

(defn join-room-api [room-id]
  (-> (js/fetch (str "/api/rooms/join?room-id=" room-id) #js {:method "POST"})
      (.then (fn [response] (.text response)))
      (.then (fn [edn-text]
               (reset! rooms (reader/read-string edn-text))))
      (.catch (fn [error]
                (js/console.error "Error joining room:" error)))))

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
             :disabled (contains? (:room/players room) @player-name)
             :style {:padding "8px 16px"
                     :background-color (if (contains? (:room/players room) @player-name)
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
                           (reset! page [:page/game {:room-id (:room/id room)}])
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
    [joinable-rooms (fetch-atom {:url "/api/rooms/joinable"})]
    (when (seq @joinable-rooms)
      [:div.joinable-rooms
       {:style {:margin-top "30px" :text-align "center"}}
       [:h3 "Rooms I Can Join"]
       (for [room @joinable-rooms]
         ^{:key (:room/id room)}
         [room-component room])])))

(defn player-rooms-component []
  (r/with-let
    [player-rooms (fetch-atom {:url "/api/rooms/player"})]
    (when (seq @player-rooms)
      [:div.joinable-rooms
       {:style {:margin-top "30px" :text-align "center"}}
       [:h3 "Rooms I'm In"]
       (for [room @player-rooms]
         ^{:key (:room/id room)}
         [room-component room])])))

(defn game-page
  [[_ {:keys [room-id]}]]
  (r/with-let
    [game-state (fetch-atom {:url (str "/api/game-state?room-id=" room-id)})
     interval (js/setInterval
               (fn []
                 (when (not= (:state/current-player @game-state) @player-name)
                   (-> (fetch {:url (str "/api/game-state?room-id=" room-id)})
                       (.then (fn [edn-text]
                                (reset! game-state edn-text))))))
               1000)
     play-card! (fn [card-value target-player-name guessed-value]
                  (let [form-data (doto (js/FormData.)
                                    (.append "card" (str card-value)))]
                    (when target-player-name
                      (.append form-data "target" target-player-name))
                    (when guessed-value
                      (.append form-data "guessed-value" (str guessed-value)))
                    (let [options (clj->js {:method "POST"
                                            :body form-data})]
                      (-> (js/fetch (str "/api/play-card?room-id=" room-id) options)
                          (.then (fn [response] (.text response)))
                          (.then (fn [edn-text]
                                   (reset! game-state (reader/read-string edn-text))))
                          (.catch (fn [error]
                                    (js/console.error "Error playing card:" error)))))))

     draw-card! (fn []
                  (-> (js/fetch (str "/api/draw-card?room-id=" room-id) #js {:method "POST"})
                      (.then (fn [response] (.text response)))
                      (.then (fn [edn-text]
                               (reset! game-state (reader/read-string edn-text))))
                      (.catch (fn [error]
                                (js/console.error "Error drawing card:" error)))))
     fns {:play-card! play-card!
          :draw-card! draw-card!}]
    [:div.game-page
     {:style {:max-width "800px"
              :margin "0 auto"
              :padding "20px"
              :font-family "Arial, sans-serif"}}
     [game-status-component @game-state fns]
     [outer-player-component @game-state fns]]
    (finally
      (js/clearInterval interval))))


(defn home-page []
  (let [username-input (r/atom "")]
    (fn []
      [:<>
       [:div.actions
        {:style {:margin-top "30px" :text-align "center"}}
        [:div {:style {:margin-bottom "15px"}}
         (if @player-name
           ;; Username already set in cookie - display as non-editable text
           [:div {:style {:padding "8px 12px"
                          :font-size "14px"
                          :font-weight "bold"
                          :color "#333"}}
            (str "Username: " @player-name)]
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


(defn app []
  (case (first @page)
    :page/game [game-page @page]
    :page/home [home-page]))

;; after setting name, refresh page (or other) to show list of rooms

;; move home page (rooms list) components and room/game page components to their own namespaces
;; might need a shared namespace for some client side fns

;; pass room-id param for game fns (only return a single room from backend)

;; when game is done, room state needs to be updated

;; use reitit for page routes on frontend

;; change demo directory to client


;; use proper session for the cookie (and fix how the frontend gets the user's name)

;; review "security"
