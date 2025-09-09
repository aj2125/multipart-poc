import math

def reward_function(params):
    waypoints = params["waypoints"]
    prev_idx, next_idx = params["closest_waypoints"]
    x, y = params["x"], params["y"]
    heading = params["heading"]
    width = params["track_width"]
    d2c = params["distance_from_center"]
    left = params.get("is_left_of_center", True)
    speed = params.get("speed", 1.0)
    steer = params.get("steering_angle", 0.0)
    on_track = params.get("all_wheels_on_track", True)
    progress = params.get("progress", 0.0)
    steps = params.get("steps", 1)

    if not on_track:
        return 1e-4

    LOOKAHEAD = 5
    STRAIGHT_WEAVE_THRESHOLD = 7.0
    STALL_SPEED = 1.5
    STEPS_PER_SECOND = 15.0
    TARGET_LAP_TIME = 22.0
    PACE_WEIGHT = 0.35
    EDGE_DAMP_NEAR_BOUNDARY = 0.2

    nwp = len(waypoints)
    def wp(i): return waypoints[i % nwp]
    def vec(a, b): return (b[0]-a[0], b[1]-a[1])
    def ang(v): return math.degrees(math.atan2(v[1], v[0]))
    def diff(a, b):
        d = a - b
        while d > 180: d -= 360
        while d < -180: d += 360
        return d
    def dist(i):
        px, py = wp(i)
        return math.hypot(px - x, py - y)

    h0  = ang(vec(wp(prev_idx), wp(next_idx)))
    h1a = ang(vec(wp(next_idx),     wp(next_idx + LOOKAHEAD)))
    h1b = ang(vec(wp(next_idx + 1), wp(next_idx + 1 + LOOKAHEAD)))
    h1  = 0.5 * (h1a + h1b)
    d_h = diff(h1, h0)
    curv = abs(d_h) / max(1, LOOKAHEAD)

    if curv < 1.4:
        turn = "straight"
    elif curv < 5.0:
        turn = "gentle"
    else:
        turn = "sharp"

    inside_left = (d_h > 0)

    apex_idx  = next_idx + (3 if turn == "gentle" else 8)
    APEX_SPAN = 2 if turn == "gentle" else 1
    win_apex  = range(apex_idx - APEX_SPAN, apex_idx + APEX_SPAN + 1)
    win_entry = range(next_idx + 1, max(next_idx + 1, apex_idx - APEX_SPAN))
    win_post  = range(apex_idx + APEX_SPAN + 1, apex_idx + APEX_SPAN + 1 + (5 if turn != "straight" else 6))

    d_ent = min((dist(i) for i in win_entry), default=1e9)
    d_apx = min((dist(i) for i in win_apex),  default=1e9)
    d_post = min((dist(i) for i in win_post), default=1e9)

    phase = "straight"
    if turn != "straight":
        m = min(d_ent, d_apx, d_post)
        phase = "entry" if m == d_ent else ("apex" if m == d_apx else "post")

    reward = 1.0
    hdg_err = abs(diff(heading, h0))
    align = max(0.0, 1.0 - min(hdg_err, 90) / 90.0)
    reward *= 0.5 + 0.5 * align

    half_w = width / 2.0
    norm = d2c / max(1e-6, half_w)

    reward *= 1.0 + 0.05 * (1.0 - min(norm, 1.0))

    if phase == "entry":
        want_left = not inside_left
        reward *= 1.6 if (left == want_left and 0.25 <= norm <= 0.8) else 0.9
    elif phase == "apex":
        want_left = inside_left
        reward *= (1.2 + 0.8 * min(norm, 0.9)) if left == want_left else 0.8
    elif phase == "post":
        want_left = not inside_left
        if (left == want_left) and (norm >= 0.2):
            reward *= 1.3
    else:
        reward *= 1.3 if norm <= 0.2 else 0.9

    def steps_until_turn(i0, th=6.0, horizon=5):
        p = ang(vec(wp(i0), wp(i0+1)))
        for j in range(1, horizon+1):
            h = ang(vec(wp(i0+j), wp(i0+j+1)))
            if abs(diff(h, p)) >= th:
                return j
            p = h
        return 999

    steps_to_turn = steps_until_turn(next_idx, th=6.0, horizon=5)
    chained = steps_to_turn <= 3

    if turn == "straight":
        s = max(0.0, min(speed / 3.8, 1.0))
        mult = 1.0 + 0.15 + (0.70 - 0.15) * s
        p = ang(vec(wp(next_idx), wp(next_idx+1)))
        turn_soon = False
        for j in range(1, 5):
            h = ang(vec(wp(next_idx+j), wp(next_idx+j+1)))
            if abs(diff(h, p)) >= 6.0:
                turn_soon = True
                break
            p = h
        if turn_soon:
            mult *= 0.90
        reward *= mult
    elif turn == "gentle":
        base = 0.92 + min(speed / 4.0, 0.45)
        if curv > 3.6:
            base = 0.90 + min(speed / 4.0, 0.40)
        reward *= base
    else:
        if phase == "entry":
            if chained:
                reward *= 0.70 if speed > 1.8 else 1.05
            else:
                reward *= 0.75 if speed > 1.9 else 1.07
        elif phase == "apex":
            reward *= 0.8 + min(speed / 4.0, 0.4)
        else:
            if chained:
                reward *= 1.08 + min(speed / 3.8, 0.35)
            else:
                reward *= 1.12 + min(speed / 3.8, 0.45)

    if (turn == "straight") and (curv < 0.8) and (abs(steer) > STRAIGHT_WEAVE_THRESHOLD):
        reward *= 0.8

    margin = min(0.25, 0.10 + 0.06 * (speed / 4.0) + 0.015 * curv)
    if (turn == "gentle") and (curv >= 1.6):
        margin *= 0.9
    if norm > (1.0 - margin):
        danger = max(1.2, 1.0 + 2.0 * (speed / 4.0) + 0.5 * curv)
        reward *= max(0.05, 0.35 / danger)
    if norm > 0.965:
        reward *= EDGE_DAMP_NEAR_BOUNDARY

    if norm > 0.90:
        steering_back = (left and steer < -3.0) or ((not left) and steer > 3.0)
        if steering_back:
            reward *= 1.10

    signed_lat = (norm if left else -norm)
    if phase == "entry":
        tgt = -0.55 if (d_h > 0) else 0.55
    elif phase == "apex":
        tgt = 0.70 if (d_h > 0) else -0.70
    elif phase == "post":
        tgt = -0.35 if (d_h > 0) else 0.35
    else:
        tgt = 0.0
    err = abs(signed_lat - tgt)
    smooth = 1.0 - min(err / 0.35, 1.0) ** 2
    reward *= (1.0 + 0.06 * smooth)

    target_steps = STEPS_PER_SECOND * TARGET_LAP_TIME
    pace_now = progress / max(1.0, steps)
    target_pace = 100.0 / max(1.0, target_steps)
    pace_ratio = max(0.0, min(pace_now / target_pace, 2.0))
    reward *= (1.0 - PACE_WEIGHT) + (PACE_WEIGHT * pace_ratio)

    if progress >= 99.5:
        beat = max(0.0, (target_steps - steps) / max(1.0, target_steps))
        reward *= (1.0 + 2.0 * beat)

    if (turn == "straight") and (speed < STALL_SPEED):
        reward *= 0.85

    return float(max(reward, 1e-4))