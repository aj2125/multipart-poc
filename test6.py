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

    LOOKAHEAD = 6
    ARC_LOOKAHEAD = 10
    STRAIGHT_WEAVE_THRESHOLD = 9.5
    STALL_SPEED = 1.5
    STEPS_PER_SECOND = 15.0
    TARGET_LAP_TIME = 18.6
    PACE_WEIGHT = 0.34
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

    def arc_same_sign():
        base = ang(vec(wp(next_idx), wp(next_idx+1)))
        sign = 0
        same = 0
        for j in range(1, ARC_LOOKAHEAD):
            h = ang(vec(wp(next_idx+j), wp(next_idx+j+1)))
            dh = diff(h, base)
            s = 1 if dh > 0 else (-1 if dh < 0 else 0)
            if s != 0:
                if sign == 0:
                    sign = s
                elif s == sign:
                    same += 1
                else:
                    return 0, 0
            base = h
        return (sign, same)

    arc_sign, arc_len = arc_same_sign()

    if curv < 1.0:
        turn = "straight"
    elif curv < 4.2:
        turn = "gentle"
    else:
        turn = "sharp"

    inside_left = (d_h > 0)

    apex_idx  = next_idx + (3 if turn == "gentle" else 8)
    APEX_SPAN = 2 if turn == "gentle" else 1
    win_apex  = range(apex_idx - APEX_SPAN, apex_idx + APEX_SPAN + 1)
    win_entry = range(next_idx + 1, max(next_idx + 1, apex_idx - APEX_SPAN))
    win_post  = range(apex_idx + APEX_SPAN + 1, apex_idx + APEX_SPAN + 1 + (5 if turn != "straight" else 6))

    d_ent  = min((dist(i) for i in win_entry), default=1e9)
    d_apx  = min((dist(i) for i in win_apex),  default=1e9)
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

    if turn == "straight":
        reward *= 1.0 + 0.02 * (1.0 - min(norm, 1.0))

    on_arc = (turn != "sharp") and (arc_sign != 0) and (arc_len >= 5)

    if turn == "straight":
        s = max(0.0, min(speed / 4.1, 1.0))
        mult = 1.0 + 0.16 + (0.80 - 0.16) * s
        p = ang(vec(wp(next_idx), wp(next_idx+1)))
        turn_soon = False
        for j in range(1, 5):
            h = ang(vec(wp(next_idx+j), wp(next_idx+j+1)))
            if abs(diff(h, p)) >= 6.0:
                turn_soon = True
                break
            p = h
        if turn_soon:
            mult *= 0.97
        reward *= mult
    elif turn == "gentle":
        base = 0.96 + min(speed / 4.1, 0.58)
        if curv > 4.0:
            base = 0.94 + min(speed / 4.1, 0.50)
        reward *= base
    else:
        if phase == "entry":
            reward *= 0.75 if speed > 1.9 else 1.07
        elif phase == "apex":
            reward *= 0.8 + min(speed / 4.1, 0.4)
        else:
            reward *= 1.20 + min(speed / 4.1, 0.58)

    if on_arc:
        desired = max(6.0, min(14.0, (abs(d_h) * 0.8) + (speed * 1.2)))
        correct_sign = (steer > 0 and arc_sign > 0) or (steer < 0 and arc_sign < 0)
        mag_err = abs(abs(steer) - desired)
        hold = max(0.0, 1.0 - (mag_err / 12.0))
        if correct_sign:
            reward *= (1.0 + 0.10 * hold)
        else:
            reward *= 0.94
        if hold > 0.6 and norm < 0.85:
            reward *= (1.025 + 0.035 * min(speed / 4.1, 1.0))

    if (turn == "gentle") and on_arc and (phase != "apex"):
        outside_is_left = not (d_h > 0)
        if (left == outside_is_left) and (norm >= 0.60):
            reward *= 1.06

    if (turn == "straight") and (curv < 1.0) and (abs(steer) > STRAIGHT_WEAVE_THRESHOLD):
        reward *= 0.8

    margin = min(0.25, 0.10 + 0.055 * (speed / 4.1) + 0.012 * curv)
    if (turn == "gentle") and (curv >= 1.6):
        margin *= 0.9
    if norm > (1.0 - margin):
        danger = max(1.2, 1.0 + 2.0 * (speed / 4.1) + 0.5 * curv)
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