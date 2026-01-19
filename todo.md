;; after setting name, refresh page (or other) to show list of rooms

;; move home page (rooms list) components and room/game page components to their own namespaces
;; might need a shared namespace for some client side fns

;; pass room-id param for game fns (only return a single room from backend)

;; when game is done, room state needs to be updated

;; use reitit for page routes on frontend


;; use proper session for the cookie (and fix how the frontend gets the user's name)

;; review "security"
________

;; 1) fix the front end

;; 2) multiple rounds

;; 3) make use of the malli schema to validate the state
;;     https://github.com/metosin/malli/blob/master/docs/function-schemas.md#defn-schemas


(comment

;; /  if not "logged in"
;;     show a form to enter their name
;;       when submit, create a cookie for the player (name="Micah")
;;       redirect to /
;;    else
;;     ✅ see rooms they are in
;;     ✅ see open "rooms" (rooms in pre-game state)
;;     "new game" button
;;         when submit, ✅ create a new lobby and add player to it
;;         redirect to /room/15

;; /room/15
;;    page is polling  (poll? long polling? websockets?)
;;
;;    if not logged in
;;       show a "you're not logged in" message
;;    if in "pre-game" state
;;       if player is not part of room
;;          show a button to "join the room"
;;          when submit, add player to room
;;       else player is part of room
;;          show who is in room
;;          "start game" button
;;             when submit, swap room with new game state
;;    else  --if in game state
;;       if player part of game
;;          show the game
;;       else
;;          show a "you're not part of this game" message
;;   else if in end of game state
;;       show end of game screen, 
;;         w/ "replay" button 
;;         (which immediately creats and starts a new game with the same players)
;;         w/ home button
;;   if game does not exist
;;      show a "game not found" message

;; front end will need two tabs to test

;; when does a game get removed from the active games?


;; routes namespace, which talks to the games management namespace and the game namespace

)