(ns client.game
  (:require
   [reagent.core :as r]
   [duck-dynasty.game :as dd]
   [clojure.string :as str]
   [cljs.reader :as reader]
   [client.state :as state]))

(defn card-component
  ([card] (card-component card nil))
  ([card on-click] (card-component card on-click nil))
  ([card on-click style]
   [:div.card
    {:on-click on-click
     :style (merge {:border "1px solid #ccc"
                    :padding "10px"
                    :border-radius "4px"
                    :background-color "#f9f9f9"
                    :display "flex"
                    :flex-direction "column"
                    :align-items "center"
                    :width "140px"
                    :cursor (if on-click "pointer" "default")}
                   style)}
    [:img {:src (str "/card?value=" (:card/value card))
           :alt (or (:card/name card) "Card")
           :style {:display "block"
                   :width "100%"
                   :height "auto"
                   :border-radius "4px"}}]]))

(defn player-hand-component [state {:keys [draw-card!]} player-name on-card-click]
  (let [hand (dd/player-hand state player-name)
        is-current-player? (= player-name (:state/current-player state))
        has-one-card? (= (count hand) 1)
        ;; key by position: a hand can hold two identical cards (e.g. two Wizards)
        cards (map-indexed
               (fn [i card]
                 ^{:key i}
                 [card-component card (when on-card-click #(on-card-click card))])
               hand)
        draw-button (when (and is-current-player? has-one-card?)
                      [:img {:key "draw-card"
                             :src "/card?value=back"
                             :alt "Draw Card"
                             :on-click #(draw-card!)
                             :style {:width "160px"
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
       [:div
        [:div.cards
         {:style {:display "flex"
                  :flex-direction "row"
                  :flex-wrap "nowrap"
                  :justify-content "center"
                  :align-items "center"
                  :gap "10px"
                  :opacity (when-not is-current-player? "0.5")
                  :filter (when-not is-current-player? "grayscale(100%)")}}
         (doall cards)
         draw-button]
        (when-not is-current-player?
          [:p {:style {:text-align "center"
                       :color "#666"
                       :font-style "italic"
                       :margin "8px 0 0 0"}}
           (str "Waiting for " (:state/current-player state) "...")])])]))

;; TODO from Jeff - make smaller components e.g. for target and guess
;; do not pass state around - below should not get state as an argument, should get fns that alter state (state defined in top level component)
;; within each room, i'll have a game state. all components shuld get an immutable version of state (and fns should alter state)

(defn card-needs-target? [card]
  (some? (:card/targeting-rule card)))

(defn card-needs-guess? [card]
  (= (:card/value card) 1)) ; Minion needs a guess

(defn play-card-form [state {:keys [play-card! cancel!]} _player-name card]
  (let [target-players (dd/targetable-players state (:card/value card))
        needs-target? (card-needs-target? card)
        needs-guess? (card-needs-guess? card)
        target (r/atom (first target-players))
        guessed-value (r/atom 2)]
    (fn []
      [:div.play-card-form
       {:on-click #(when (= (.-target %) (.-currentTarget %)) (cancel!))
        :style {:position "fixed"
                :top "0" :left "0"
                :width "100%" :height "100%"
                :background-color "rgba(0,0,0,0.6)"
                :display "flex"
                :align-items "center"
                :justify-content "center"
                :z-index "1000"}}
       [:div {:style {:background-color "#fff"
                      :border-radius "12px"
                      :padding "30px"
                      :min-width "260px"
                      :box-shadow "0 8px 32px rgba(0,0,0,0.3)"}}
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
         "Play Card"]
        [:button {:on-click cancel!
                  :style {:margin-left "10px"
                          :padding "8px 16px"
                          :background-color "#ccc"
                          :border "none"
                          :border-radius "4px"
                          :cursor "pointer"}}
         "Cancel"]]])))

(defn outer-player-component [_state _fns]
  (let [selected-card (r/atom nil)]
    (fn [state {:keys [play-card!] :as fns}]
      (let [current-player (:state/current-player state)
            hand (dd/player-hand state current-player)
            is-current-player? (= @state/player-name current-player)
            can-play? (and is-current-player? (= (count hand) 2))
            play-card! (fn [& args]
                         (reset! selected-card nil)
                         (apply play-card! args))
            on-card-click (when can-play?
                            (fn [card]
                              (if (or (card-needs-target? card) (card-needs-guess? card))
                                (reset! selected-card card)
                                (play-card! (:card/value card) nil nil))))]
        (when (not (dd/game-over? state))
          [:div.current-player
           {:style {:background-color "#e3f2fd"
                    :padding "12px"
                    :border-radius "8px"
                    :margin "10px 0"}}
           [player-hand-component state fns @state/player-name on-card-click]
           (when (and can-play? (some #{@selected-card} hand))
             ^{:key (:card/value @selected-card)}
             [play-card-form state
              (assoc fns
                     :play-card! play-card!
                     :cancel! #(reset! selected-card nil))
              current-player
              @selected-card])])))))

(defn current-turn-component [current-player]
  [:h2 {:style {:margin "0" :font-size "1.2em"}}
   (str "Currently " current-player "'s turn")])

(defn round-scores-display [round-winners]
  (let [scores (for [[player wins] round-winners]
                 (str player ": " wins))]
    (if (empty? scores)
      "No rounds completed yet"
      (str/join " | " scores))))

(defn game-status-component [state {:keys [replay!]}]
  [:div.game-status
   {:style {:background-color "#fff3cd"
            :padding "8px 15px"
            :border-radius "8px"
            :margin "0 0 10px 0"}}
   (if (dd/game-over? state)
     (let [winners (dd/game-winners state)
           winner-text (str/join " and " winners)]
       [:div
        [:h2 {:style {:color "#856404"}} "Game Over!"]
        [:p {:style {:font-size "1.2em" :font-weight "bold"}}
         (str winner-text " wins the game!")]
        [:p {:style {:font-size "1em" :margin-top "10px"}}
         (str "Final Score: " (round-scores-display (:state/round-win-counts state)))]
        [:button {:on-click replay!
                  :style {:margin-top "15px"
                          :margin-right "10px"
                          :padding "10px 20px"
                          :background-color "#4CAF50"
                          :color "white"
                          :border "none"
                          :border-radius "4px"
                          :cursor "pointer"
                          :font-size "16px"}}
         "Play again"]
        [:button {:on-click #(state/navigate-to! [:page/home {}])
                  :style {:margin-top "15px"
                          :padding "10px 20px"
                          :background-color "#2196F3"
                          :color "white"
                          :border "none"
                          :border-radius "4px"
                          :cursor "pointer"
                          :font-size "16px"}}
         "Back to Home"]])
     [:div {:style {:display "flex" :flex-wrap "wrap" :column-gap "20px"}}
      [:p {:style {:margin "4px 0"}}
       (str "Round " (:state/round state) " of " dd/best-of)]
      [:p {:style {:margin "4px 0" :font-weight "bold"}}
       (str "Score: " (round-scores-display (:state/round-win-counts state)))]
      [:p {:style {:margin "4px 0"}}
       (str "Active Players: " (str/join ", " (dd/active-players state)))]
      [:p {:style {:margin "4px 0"}}
       (str "Remaining Cards: " (count (:state/deck state)))]])])

(defn last-played-component [last-played]
  [:div.last-played
   {:style {:display "flex"
            :align-items "center"
            :gap "15px"}}
   (if-let [{:last-played/keys [player-name card]} last-played]
     [:div
      [:p {:style {:margin "0 0 5px 0"}} (str "Last card played (by " player-name ")")]
      [card-component card nil {:width "90px"
                                :padding "6px"
                                :opacity "0.6"}]]
     [:p {:style {:color "#999" :font-style "italic" :margin "0"}} "No cards played yet."])])

(defn abbot-reveal-modal [reveal dismiss!]
  [:div {:style {:position "fixed"
                 :top "0" :left "0"
                 :width "100%" :height "100%"
                 :background-color "rgba(0,0,0,0.6)"
                 :display "flex"
                 :align-items "center"
                 :justify-content "center"
                 :z-index "1000"}}
   [:div {:style {:background-color "#fff"
                  :border-radius "12px"
                  :padding "30px"
                  :text-align "center"
                  :max-width "300px"
                  :width "90%"
                  :box-shadow "0 8px 32px rgba(0,0,0,0.3)"}}
    [:h2 {:style {:margin-top "0" :margin-bottom "8px"}} "Abbot Reveal"]
    [:p {:style {:color "#555" :margin-bottom "20px"}}
     (str (:abbot-reveal/target-player-name reveal) "'s card:")]
    [card-component (:abbot-reveal/card reveal)]
    [:button {:on-click dismiss!
              :style {:margin-top "20px"
                      :padding "10px 24px"
                      :background-color "#4CAF50"
                      :color "white"
                      :border "none"
                      :border-radius "6px"
                      :cursor "pointer"
                      :font-size "1em"}}
     "Got it"]]])

(defn game-log-component [log]
  [:div.game-log
   {:style {:background-color "#f5f5f5"
            :border "1px solid #ddd"
            :border-radius "8px"
            :padding "15px"
            :width "280px"
            :flex-shrink "0"}}
   [:h3 {:style {:margin-top "0" :margin-bottom "10px" :font-size "1em" :color "#555"}} "Game Log"]
   (if (empty? log)
     [:p {:style {:color "#999" :font-style "italic" :margin "0"}} "No actions yet."]
     [:ul {:style {:list-style "none"
                   :margin "0"
                   :padding "0"
                   :max-height "calc(100vh - 420px)"
                   :overflow-y "auto"}}
      (map-indexed
       (fn [i entry]
         ^{:key i}
         [:li {:style {:padding "4px 0"
                       :border-bottom "1px solid #eee"
                       :font-size "0.9em"
                       :color "#333"}}
          (:message/content entry)])
       (reverse log))])])

(defn round-ending-entries
  "The last 2 log entries of the previous round. The log ends with
   \"Round N ended\" and \"Round N+1 started\", so skip those."
  [log]
  (->> log (drop-last 2) (take-last 2)))

(defn round-result-component [{:round-result/keys [round reason winners final-hands]} log]
  [:div {:style {:text-align "center"
                 :margin-bottom "30px"}}
   [:h2 {:style {:margin "0 0 10px 0"}}
    (str "Round " round " won by " (str/join " and " winners))]
   (when (= reason :deck-empty)
     [:p {:style {:margin "0 0 10px 0" :color "#555"}}
      "The deck ran out, so the highest card wins."])
   (for [[i entry] (map-indexed vector (round-ending-entries log))]
     ^{:key i}
     [:p {:style {:margin "0 0 5px 0" :color "#333"}} (:message/content entry)])
   (when (= reason :deck-empty)
     [:div {:style {:display "flex" :gap "10px" :justify-content "center"}}
      (for [[player-name [card]] final-hands]
        ^{:key player-name}
        [:div
         [:p {:style {:margin "0 0 5px 0"}} player-name]
         [card-component card]])])])

(defn new-round-screen [round last-round-result log on-start]
  [:div {:style {:display "flex"
                 :flex-direction "column"
                 :align-items "center"
                 :justify-content "center"
                 :min-height "300px"
                 :padding "40px"}}
   (when last-round-result
     [round-result-component last-round-result log])
   [:h1 {:style {:font-size "2.5em"
                 :margin-bottom "30px"
                 :color "#333"}}
    (str "Round " round)]
   [:button {:on-click on-start
             :style {:padding "15px 40px"
                     :background-color "#4CAF50"
                     :color "white"
                     :border "none"
                     :border-radius "8px"
                     :cursor "pointer"
                     :font-size "1.2em"}}
    "Start"]])

(defn game-page
  [[_ {:keys [room-id]}]]
  (r/with-let
    [game-state (state/fetch-atom {:url (str "/api/game-state?room-id=" room-id)})
     round-started (r/atom false)
     last-round (r/atom nil)
     interval (js/setInterval
               (fn []
                 ;; keep polling after the game ends, to follow a replay
                 (when (or (not= (:state/current-player @game-state) @state/player-name)
                           (dd/game-over? @game-state))
                   (-> (state/fetch {:url (str "/api/game-state?room-id=" room-id)})
                       (.then (fn [edn-text]
                                (if-let [next-room-id (:room/next-room-id edn-text)]
                                  (state/navigate-to! [:page/game {:room-id next-room-id}])
                                  (reset! game-state edn-text)))))))
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
     dismiss-abbot-reveal! (fn []
                             (-> (js/fetch (str "/api/dismiss-abbot-reveal?room-id=" room-id) #js {:method "POST"})
                                 (.then (fn [response] (.text response)))
                                 (.then (fn [edn-text]
                                          (reset! game-state (reader/read-string edn-text))))
                                 (.catch (fn [error]
                                           (js/console.error "Error dismissing abbot reveal:" error)))))
     replay! (fn []
               (-> (js/fetch (str "/api/rooms/replay?room-id=" room-id) #js {:method "POST"})
                   (.then (fn [response] (.text response)))
                   (.then (fn [edn-text]
                            (state/navigate-to! [:page/game {:room-id (:room-id (reader/read-string edn-text))}])))
                   (.catch (fn [error]
                             (js/console.error "Error starting replay:" error)))))
     fns {:play-card! play-card!
          :draw-card! draw-card!
          :replay! replay!}]
    (let [state @game-state
          current-round (:state/round state)
          abbot-reveal (:state/abbot-reveal state)
          my-abbot-reveal (when (= @state/player-name (:abbot-reveal/abbot-player-name abbot-reveal))
                            abbot-reveal)]
      (when (not= current-round @last-round)
        (reset! last-round current-round)
        (reset! round-started false))
      [:div.game-page
       {:style {:max-width "1200px"
                :margin "0 auto"
                :padding "10px 20px"
                :font-family "Arial, sans-serif"}}
       [:button {:on-click #(state/navigate-to! [:page/home {}])
                 :style {:margin-bottom "10px"
                         :padding "6px 12px"
                         :background-color "#fff"
                         :border "1px solid #ccc"
                         :border-radius "4px"
                         :cursor "pointer"}}
        "← Home"]
       (when my-abbot-reveal
         [abbot-reveal-modal my-abbot-reveal dismiss-abbot-reveal!])
       (if (and (not @round-started) (empty? (:state/discard-pile state)) (not (dd/game-over? state)))
         [new-round-screen current-round (:state/last-round-result state) (:state/log state) #(reset! round-started true)]
         [:div {:style {:display "flex" :gap "20px" :align-items "flex-start"}}
          [:div {:style {:flex "1" :min-width "0"}}
           [game-status-component state fns]
           [outer-player-component state fns]]
          [:div {:style {:display "flex" :flex-direction "column" :gap "10px"}}
           (when-not (dd/game-over? state)
             [current-turn-component (:state/current-player state)])
           [last-played-component (:state/last-played state)]
           [game-log-component (:state/log state)]]])])
    (finally
      (js/clearInterval interval))))

