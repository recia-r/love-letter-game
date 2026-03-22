IN PROGRESS


NEXT

; w/ raf - use proper session for the cookie (and fix how the frontend gets the user's name)

;; 3 ways to do "secure" "tamper-proof" identity cookies

;; to create an account, enter name + password

;; store that

;; to log in ("authentication")
;; ask for username, password; look them up, then...
;; want to store a user's id in a cookie, in a tamper proof way

;; v1) encryption   ring.mddleware.session.cookie
;;       message + secret ==f1==> 034gh203...gh023g4h
;;       034gh203...gh023g4h + secret ==f2==> message
;;
;;       + don't need to store anything in the backend

;; v2) signing
;;       message + secret ==f1==> message + 1135...g23g ("hmac" / "message authenitcation code")
;;       message + 1135...g23g + secret ==f2==>  valid / invalid
;;
;;       + compared to encryption, much smaller size and faster
;;       + like encryption, dont' need to store anything in backend

;; v3) session ids ring.mddleware.session.memory <== will use this one
;;     generate a large random number (that can't be statistically guessed)
;;     store in the backend  {random-number-for-user-1 user-1-id
                              random-number-for-user-2 user-2-id}
;;     give the number as a cookie
;;     when a request comes in (with the number in the cookie), look up the corresponding user-id
;;
;;     + makes it easy to track all the devices where a user is logged in, and log them out independently

INBOX


;; abbot show 

on game end screen, button to "replay" which moves both players into a new game

;; a log of what happened
;; use reitit for page routes on frontend

winner of previous round should start next round
trying to access a room that doesnt exist should return 404


;; review "security" - broadly, think about all the ways someone might try to cheat

fix the way we get user name

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
;;    TODO if not logged in
;;       show a "you're not logged in" message
;;    ✅if in "pre-game" state
;;       if player is not part of room
;;          show a button to "join the room"
;;          when submit, add player to room
;;       else player is part of room
;;          show who is in room
;;          "start game" button
;;             when submit, swap room with new game state
;;    ✅else  --if in game state
;;       if player part of game
;;          show the game
;;       TODO else
;;          show a "you're not part of this game" message
;;   else if in end of game state
;;       show end of game screen, 
;;         w/ "replay" button 
;;         (which immediately creats and starts a new game with the same players)
;;         w/ home button
;;   if game does not exist
;;      show a "game not found" message




)

main.js edits DOM
user action triggers event which is handled (request to back-end)
front-end will put data from response into app-db
components with changes will get re-rendered


reagent: maintains a mapping between ratoms and components that deref them

game-state 
/  |   \
c1 c2   c3


         game-state
         /   |    \
      user cards other   (re-frame subs; reagent cursors or reactions)
       |     \   /  |
      c1      c2    c3


browser makes request to localhost:8200
index.html returned as this is defined in the handler for "/"
requests main.js
first fn called is core/init (in shadow edn file {:init-fn client.core/init})
calls ui/init and render 

top level render() (render fn takes top level fn and place to put it in the DOM/index.html)

V

nested compoment function calls (which subscribe/deref atoms)

V

tree of hiccup  (and creates/mutates the state of active component-ratom relationships described abive)

V (reagent triggers entire or sub virtual-dom recalculation via react)     <------ changes of state trigger here

virtual-dom-current    =>     virtual-dom-next   (react)

 (lighterweight and faster to query;
  changes to virtual-dom don't immediately cause expesive redrawing of the interface)


V   (react applies the diff)


DOM (in-memory tree of "nodes")

V ---- drawing (expensive)

interface drawn on screen

V

user interacts with something (or some js timeout happens)

V

changes the state  -----------------------------------------------------------------^