(ns client.game
  (:require
   [reagent.core :as r]
   [duck-dynasty.game :as dd]
   [clojure.string :as str]
   [cljs.reader :as reader]
   [client.state :as state]))

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

(defn play-card-form [state {:keys [play-card!]} _player-name card]
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
        is-current-player? (= @state/player-name current-player)]
    (when (not (dd/game-over? state))
      [:div.current-player
       {:style {:background-color "#e3f2fd"
                :padding "20px"
                :border-radius "8px"
                :margin "20px 0"}}
       [:h2 {:style {:margin-top "0"}}
        (str "Currently " current-player "'s turn")]
       [player-hand-component state fns @state/player-name]
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

(defn game-page
  [[_ {:keys [room-id]}]]
  (r/with-let
    [game-state (state/fetch-atom {:url (str "/api/game-state?room-id=" room-id)})
     interval (js/setInterval
               (fn []
                 (when (not= (:state/current-player @game-state) @state/player-name)
                   (-> (state/fetch {:url (str "/api/game-state?room-id=" room-id)})
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

