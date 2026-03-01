IN PROGRESS


NEXT

when you have a card to drtaw, don't show action options

INBOX




;; use reitit for page routes on frontend


;; use proper session for the cookie (and fix how the frontend gets the user's name)

;; review "security"

fix the way we get user name


when there is no game (game-state is nil) show differnet component. 

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