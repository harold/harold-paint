(ns harold-paint.color-picker
  (:require [reagent.core :as reagent]
            [goog.events :as events])
  (:import [goog.events EventType]))

;; function hsvToRgb(h, s, v) {
;;   var r, g, b;
;;   var i = Math.floor(h * 6);
;;   var f = h * 6 - i;
;;   var p = v * (1 - s);
;;   var q = v * (1 - f * s);
;;   var t = v * (1 - (1 - f) * s);
;;   switch (i % 6) {
;;     case 0: r = v, g = t, b = p; break;
;;     case 1: r = q, g = v, b = p; break;
;;     case 2: r = p, g = v, b = t; break;
;;     case 3: r = p, g = q, b = v; break;
;;     case 4: r = t, g = p, b = v; break;
;;     case 5: r = v, g = p, b = q; break;
;;   }
;;   return [ r * 255, g * 255, b * 255 ];
;; }

(defn hsv->rgb
  "hsv in [0,1] -> rgb in [0,255]"
  [h s v]
  (let [i (Math/floor (* h 6))
        f (* h (- 6 i))
        p (* v (- 1 s))
        q (* v (- 1 (* f s)))
        t (* v (- 1 (* (- 1 f) s)))]
    (->> (condp = (mod i 6)
           0 [v t p]
           1 [q v p]
           2 [p v t]
           3 [p q v]
           4 [t p v]
           5 [v p q])
         (mapv #(* 255.0 %)))))

;; function rgbToHsv(r, g, b) {
;;   r /= 255, g /= 255, b /= 255;
;;   var max = Math.max(r, g, b), min = Math.min(r, g, b);
;;   var h, s, v = max;
;;   var d = max - min;
;;   s = max == 0 ? 0 : d / max;
;;   if (max == min) {
;;     h = 0; // achromatic
;;   } else {
;;     switch (max) {
;;       case r: h = (g - b) / d + (g < b ? 6 : 0); break;
;;       case g: h = (b - r) / d + 2; break;
;;       case b: h = (r - g) / d + 4; break;
;;     }
;;     h /= 6;
;;   }
;;   return [ h, s, v ];
;; }

(defn rgb->hsv
  "rgb in [0,255] -> hsv -> [0,1]"
  [r g b]
  (let [[r g b] (map #(/ % 255.0) [r g b])
        maximum (max r g b)
        minimum (min r g b)
        v maximum
        d (- maximum minimum)
        s (if (zero? maximum) 0 (/ d maximum))
        h (if (= maximum minimum)
            0
            (/ (condp = maximum
                 r (+ (/ (- g b) d) (if (< g b) 6 0))
                 g (+ (/ (- b r) d) 2)
                 b (+ (/ (- r g) d) 4))
               6))]
    [h s v]))

(defn- filled-circle
  [canvas-ctx x y r color]
  (aset canvas-ctx "fillStyle" color)
  (.beginPath canvas-ctx)
  (.arc canvas-ctx x y r 0 (* 2 Math/PI))
  (.fill canvas-ctx))

(defn- render
  [ctx]
  (let [{:keys [canvas-ctx color]} (-> ctx :state* deref)
        [r g b] color
        w (-> canvas-ctx .-canvas .-clientWidth)
        h (-> canvas-ctx .-canvas .-clientHeight)]
    (dotimes [i 16]
      (aset canvas-ctx "fillStyle" (str "rgb("(* 16 i)","g","b")"))
      (.fillRect canvas-ctx 0 (* (- 15 i) (quot h 16)) (quot w 9) (quot h 16)))
    (filled-circle canvas-ctx (quot w 18) (- h (* 1.0 h (/ r 255))) 6 "#000000")
    (filled-circle canvas-ctx (quot w 18) (- h (* 1.0 h (/ r 255))) 4 "#ffffff")
    (dotimes [i 16]
      (aset canvas-ctx "fillStyle" (str "rgb("r","(* 16 i)","b")"))
      (.fillRect canvas-ctx (quot w 9) (* (- 15 i) (quot h 16)) (quot w 9) (quot h 16)))
    (filled-circle canvas-ctx (* 3 (quot w 18)) (- h (* 1.0 h (/ g 255))) 6 "#000000")
    (filled-circle canvas-ctx (* 3 (quot w 18)) (- h (* 1.0 h (/ g 255))) 4 "#ffffff")
    (dotimes [i 16]
      (aset canvas-ctx "fillStyle" (str "rgb("r","g","(* 16 i)")"))
      (.fillRect canvas-ctx (* 2 (quot w 9)) (* (- 15 i) (quot h 16)) (quot w 9) (quot h 16)))
    (filled-circle canvas-ctx (* 5 (quot w 18)) (- h (* 1.0 h (/ b 255))) 6 "#000000")
    (filled-circle canvas-ctx (* 5 (quot w 18)) (- h (* 1.0 h (/ b 255))) 4 "#ffffff")
    (aset canvas-ctx "fillStyle" (str "rgb("r","g","b")"))
    (.fillRect canvas-ctx (quot w 3) 0 (quot w 3) h)))

(defn- on-click
  [ctx e]
  (let [state* (:state* ctx)
        {:keys [canvas-ctx color]} @state*
        [r g b] color
        w (-> canvas-ctx .-canvas .-clientWidth)
        h (-> canvas-ctx .-canvas .-clientHeight)
        x (.-offsetX e)
        color-index (cond
                      (< 0 x (quot w 9)) 0
                      (< (quot w 9) x (* 2 (quot w 9))) 1
                      (< (* 2 (quot w 9)) x (* 3 (quot w 9))) 2
                      :else nil)
        y (.-offsetY e)
        p (- 1.0 (/ y h))]
    (when color-index
      (swap! state* assoc
             :color (assoc color color-index (int (* p 255))))
      (render ctx))))

(defn color-picker
  [props]
  (let [color (or (:default-value props) [255 0 0])
        ctx {:state* (reagent/atom {:color color})
             :props props}]
    (reagent/create-class
     {:component-did-mount
      (fn [this]
        (let [canvas (-> (reagent/dom-node this)
                         (.querySelectorAll "canvas")
                         (aget 0))]
          (swap! (:state* ctx) assoc
                 :canvas-ctx (.getContext canvas "2d")
                 :mouse-down-key (events/listen canvas (.-MOUSEDOWN EventType) (partial on-click ctx))))
        (render ctx))
      :reagent-render
      (fn [props]
        [:div.color-picker
         [:canvas {:width 384 :height 128}]])})))
