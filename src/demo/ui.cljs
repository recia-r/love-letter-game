(ns demo.ui
  (:require
   [reagent.core :as r]
   [duck-dynasty.game :as dd]
   [clojure.string :as str]
   [cljs.reader :as reader]))

;; Game state atom
(defonce game-state (r/atom nil))

(defn fetch-game-state []
  (-> (js/fetch "/api/game-state")
      (.then (fn [response] (.text response)))
      (.then (fn [edn-text]
               (reset! game-state (reader/read-string edn-text))))
      (.catch (fn [error]
                (js/console.error "Error fetching game state:" error)))))

(defn start-game [player-names]
  (let [players-str (str/join "," player-names)
        form-data (doto (js/FormData.)
                    (.append "players" players-str))
        options (clj->js {:method "POST"
                          :body form-data})]
    (-> (js/fetch "/api/start-game" options)
        (.then (fn [response] (.text response)))
        (.then (fn [edn-text]
                 (reset! game-state (reader/read-string edn-text))))
        (.catch (fn [error]
                  (js/console.error "Error starting game:" error))))))

(defn play-card-api [player-name card-value target-player-name guessed-value]
  (let [form-data (doto (js/FormData.)
                    (.append "player" player-name)
                    (.append "card" (str card-value)))]
    (when target-player-name
      (.append form-data "target" target-player-name))
    (when guessed-value
      (.append form-data "guessed-value" (str guessed-value)))
    (let [options (clj->js {:method "POST"
                            :body form-data})]
      (-> (js/fetch "/api/play-card" options)
          (.then (fn [response] (.text response)))
          (.then (fn [edn-text]
                   (reset! game-state (reader/read-string edn-text))))
          (.catch (fn [error]
                    (js/console.error "Error playing card:" error)))))))

(defn draw-card-api [player-name]
  (let [form-data (doto (js/FormData.)
                    (.append "player" player-name))
        options (clj->js {:method "POST"
                          :body form-data})]
    (-> (js/fetch "/api/draw-card" options)
        (.then (fn [response] (.text response)))
        (.then (fn [edn-text]
                 (reset! game-state (reader/read-string edn-text))))
        (.catch (fn [error]
                  (js/console.error "Error drawing card:" error))))))

(defn init []
  ;; Initialize a new game with two players
  (fetch-game-state))

(defn card-component [card]
  [:div.card
   {:style {:border "1px solid #ccc"
            :padding "10px"
            :margin "5px"
            :border-radius "4px"
            :background-color "#f9f9f9"
            :display "flex"
            :flex-direction "column"
            :align-items "center"
            :max-width "200px"}}
   [:img {:src (str "/card/" (:card/value card) ".jpg")
          :alt (:card/name card)
          :style {:width "100%"
                  :height "auto"
                  :border-radius "4px"
                  :margin-bottom "10px"}}]])

(defn player-hand-component [state player-name]
  (let [hand (dd/player-hand state player-name)
        is-current-player? (= player-name (:state/current-player state))
        has-one-card? (= (count hand) 1)]
    [:div.player-hand
     {:style {:margin "10px 0"}}
     (if (empty? hand)
       [:div "No cards (eliminated)"]
       [:div.cards
        {:style {:display "flex" :flex-wrap "wrap" :align-items "center"}}
        (for [card hand]
          ^{:key (str (:card/value card) "-" (:card/name card))}
          [card-component card])
        (when (and is-current-player? has-one-card?)
          [:img {:src "/card/duckcardback.png"
                 :alt "Draw Card"
                 :on-click #(draw-card-api player-name)
                 :style {:width "200px"
                         :height "auto"
                         :margin-left "10px"
                         :cursor "pointer"
                         :border-radius "4px"
                         :border "2px solid #4CAF50"
                         :transition "transform 0.2s"}
                 :on-mouse-enter #(set! (-> % .-target .-style .-transform) "scale(1.05)")
                 :on-mouse-leave #(set! (-> % .-target .-style .-transform) "scale(1)")}])])]))

;; TODO - make smaller components e.g. for target and guess
;; do not pass state around - below should not get state as an argument, should get fns that alter state (state defined in top level component)
;; within each room, i'll have a game state. all components shuld get an immutable version of state (and fns should alter state)

(defn play-card-form [state player-name card]
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
                             (play-card-api player-name
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

(defn current-player-component [state]
  (let [current-player (:state/current-player state)
        hand (dd/player-hand state current-player)]
    [:div.current-player
     {:style {:background-color "#e3f2fd"
              :padding "20px"
              :border-radius "8px"
              :margin "20px 0"}}
     [:h2 {:style {:margin-top "0"}}
      (str "Current Player: " current-player)]
     [player-hand-component state current-player]
     (when (seq hand)
       [:div.playable-cards
        {:style {:margin-top "20px"}}
        (for [card hand]
          ^{:key (str "play-" (:card/value card))}
          [:div {:style {:margin-bottom "15px"}}
           [play-card-form state current-player card]])])]))

(defn game-status-component [state]
  [:div.game-status
   {:style {:background-color "#fff3cd"
            :padding "15px"
            :border-radius "8px"
            :margin "20px 0"}}
   (if (dd/game-over? state)
     (let [winners (dd/game-winners state)
           winner-count (count winners)
           winner-text (if (= winner-count 1)
                         (first winners)
                         (str/join " and " winners))]
       [:div
        [:h2 {:style {:color "#856404"}} "Game Over!"]
        [:p {:style {:font-size "1.2em" :font-weight "bold"}}
         (str "Winner" (when (> winner-count 1) "s") ": " winner-text)]])
     [:div
      [:p (str "Active Players: " (str/join ", " (dd/active-players state)))]
      [:p (str "Round: " (:state/round state) " / " (:state/rounds state))]
      [:p (str "Remaining Cards: " (count (:state/deck state)))]])])

(defn app []
  [:<>
   [:div.actions
    {:style {:margin-top "30px" :text-align "center"}}
    [:button {:on-click #(start-game ["Micah" "Recia"])
              :style {:padding "10px 20px"
                      :background-color "#2196F3"
                      :color "white"
                      :border "none"
                      :border-radius "4px"
                      :cursor "pointer"
                      :font-size "16px"}}
     "New Game"]]
   (if-let [state @game-state]
     [:div.app
      {:style {:max-width "800px"
               :margin "0 auto"
               :padding "20px"
               :font-family "Arial, sans-serif"}}
      [game-status-component state]
      [current-player-component state]]
     [:div "Loading game state..."])])

