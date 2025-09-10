import math

class Config:
    MAX_SPEED = 4.2
    TARGET_LAP_TIME = 17.2
    SIMULATOR_FREQUENCY = 15.0
    LOOKAHEAD_CLOSE = 6
    LOOKAHEAD_FAR = 11
    TURN_DETECTION_THRESHOLD = 5.0
    PRE_POSITION_HORIZON = 10
    CHICANE_DETECTION_WINDOW = 7
    MIN_STRAIGHT_LENGTH = 3
    STRAIGHT_MAX_CURVATURE = 0.8
    GENTLE_MAX_CURVATURE = 4.5
    STRAIGHT_STALL_THRESHOLD = 0.70
    SHARP_ENTRY_MAX_SPEED = 2.4
    SHARP_EXIT_TARGET_SPEED = 4.1
    GENTLE_CORNER_SPEED = 4.1
    BASE_WEAVE_THRESHOLD = 10.5
    LOW_SPEED_STEER_BONUS = 2.0
    STEER_BACK_THRESHOLD = 3.0
    BASE_EDGE_MARGIN = 0.09
    SPEED_MARGIN_FACTOR = 0.07
    CURVATURE_MARGIN_FACTOR = 0.018
    MAX_EDGE_MARGIN = 0.25
    STRICT_EDGE_THRESHOLD = 0.97
    class Bonus:
        OUTSIDE_STAGING = 1.07
        WRONG_SIDE_PENALTY = 0.98
        ARC_OUTSIDE = 1.08
        APEX_INSIDE_SHARP = 1.10
        APEX_INSIDE_GENTLE = 1.08
        APEX_WRONG_LINE = 0.92
        STEERING_BACK = 1.15
        EDGE_PROXIMITY = 0.50
        WEAVING = 0.75
        COMPOUND_TURN = 1.05
        FINAL_SPRINT = 1.15
    class LateralTarget:
        ENTRY_OUTSIDE = 0.60
        APEX_INSIDE_SHARP = 0.65
        APEX_INSIDE_GENTLE = 0.55
        EXIT_OUTSIDE = 0.40
    SMOOTHNESS_WEIGHT_APEX = 0.14
    SMOOTHNESS_WEIGHT_OTHER = 0.09
    PACE_WEIGHT_MAIN = 0.33
    PACE_WEIGHT_LATE = 0.20
    PACE_WEIGHT_SPRINT = 0.20
    LATE_RACE_THRESHOLD = 85.0
    SPRINT_THRESHOLD = 95.0
    FINISH_LINE_THRESHOLD = 99.5

EXPECTED_TOTAL_STEPS = Config.SIMULATOR_FREQUENCY * Config.TARGET_LAP_TIME
INV_MAX_SPEED = 1.0 / Config.MAX_SPEED
INV_EXPECTED_STEPS = 1.0 / EXPECTED_TOTAL_STEPS
INV_90_DEGREES = 1.0 / 90.0
INV_30_DEGREES = 1.0 / 30.0
INV_LOOKAHEAD_CLOSE = 1.0 / Config.LOOKAHEAD_CLOSE
INV_LOOKAHEAD_FAR = 1.0 / Config.LOOKAHEAD_FAR

APEX_OFFSET_BY_TURN_TYPE = [8, 4, 7]
APEX_SPAN_BY_TURN_TYPE = [1, 2, 1]
POST_EXIT_LENGTH = [7, 6, 6]

def normalize_angle(a):
    while a > 180.0: a -= 360.0
    while a < -180.0: a += 360.0
    return a

def calculate_heading_between_points(p, q):
    return math.degrees(math.atan2(q[1]-p[1], q[0]-p[0]))

def squared_distance(x1, y1, x2, y2):
    dx = x2 - x1; dy = y2 - y1
    return dx*dx + dy*dy

def detect_track_direction(waypoints):
    s = 0
    for i in range(len(waypoints)):
        p1 = waypoints[i]
        p2 = waypoints[(i+1) % len(waypoints)]
        p3 = waypoints[(i+2) % len(waypoints)]
        v1 = (p2[0]-p1[0], p2[1]-p1[1])
        v2 = (p3[0]-p2[0], p3[1]-p2[1])
        s += v1[0]*v2[1] - v1[1]*v2[0]
    return s > 0

def classify_turn_type(curv):
    if curv < Config.STRAIGHT_MAX_CURVATURE:
        return 0
    elif curv < Config.GENTLE_MAX_CURVATURE:
        return 1
    else:
        return 2

def calculate_track_curvature(waypoints, current_idx, next_idx, L):
    n = len(waypoints)
    w0 = waypoints[current_idx % n]
    w1 = waypoints[next_idx % n]
    h0 = calculate_heading_between_points(w0, w1)
    hs = []
    for k in range(1, L):
        i = (next_idx + k) % n
        j = (next_idx + k + 1) % n
        hs.append(calculate_heading_between_points(waypoints[i], waypoints[j]))
    h1 = sum(hs)/len(hs) if hs else h0
    d = normalize_angle(h1 - h0)
    curv = abs(d) / float(max(1, L))
    return h0, d, curv

def determine_racing_phase(x, y, waypoints, next_idx, turn_type, curvature):
    if curvature < Config.STRAIGHT_MAX_CURVATURE:
        return 0
    n = len(waypoints)
    if turn_type == 1:
        return 4
    if turn_type == 2:
        apex_offset = APEX_OFFSET_BY_TURN_TYPE[turn_type]
        apex_span = APEX_SPAN_BY_TURN_TYPE[turn_type]
        apex_center = next_idx + apex_offset
        entry_start = next_idx + 1
        entry_end = max(entry_start + 1, apex_center - apex_span)
        apex_start = apex_center - apex_span
        apex_end = apex_center + apex_span + 1
        post_start = apex_end
        post_end = post_start + POST_EXIT_LENGTH[turn_type]
        best = float('inf'); phase = 1
        for i in range(entry_start, entry_end):
            w = waypoints[i % n]
            d = squared_distance(x, y, w[0], w[1])
            if d < best: best = d; phase = 1
        for i in range(apex_start, apex_end):
            w = waypoints[i % n]
            d = squared_distance(x, y, w[0], w[1])
            if d < best: best = d; phase = 2
        for i in range(post_start, post_end):
            w = waypoints[i % n]
            d = squared_distance(x, y, w[0], w[1])
            if d < best: best = d; phase = 3
        return phase
    return 0

def analyze_upcoming_turns(waypoints, start_idx, horizon, th_deg):
    n = len(waypoints)
    turns = []
    base = calculate_heading_between_points(waypoints[start_idx % n], waypoints[(start_idx+1) % n])
    cur = base
    for s in range(1, horizon+1):
        i = (start_idx + s) % n
        j = (start_idx + s + 1) % n
        h = calculate_heading_between_points(waypoints[i], waypoints[j])
        dh = normalize_angle(h - cur)
        if abs(dh) >= th_deg:
            turns.append((s, 1.0 if dh > 0 else -1.0, abs(dh)))
            cur = h
    return turns

def is_chicane_ahead(turns, max_gap_steps):
    if len(turns) < 2: return False
    a, b = turns[0], turns[1]
    return (b[0] - a[0]) <= max_gap_steps and (a[1] * b[1] < 0)

def check_compound_turn(near, far):
    return (far > 3.0 and abs(far - near) > 2.0)

def calculate_safety_margin(speed, curvature, width):
    width_factor = min(1.0, width / 1.2)
    adjusted = Config.BASE_EDGE_MARGIN * (1.0 + (1.0 - width_factor) * 0.3)
    speed_component = Config.SPEED_MARGIN_FACTOR * (speed * INV_MAX_SPEED)
    curvature_component = Config.CURVATURE_MARGIN_FACTOR * curvature
    total = adjusted + speed_component + curvature_component
    return min(Config.MAX_EDGE_MARGIN, total)

def reward_function(params):
    if params.get("is_reversed", False): return 1e-6
    if params.get("is_offtrack", False): return 1e-5
    if not params.get("all_wheels_on_track", True): return 1e-4

    waypoints = params["waypoints"]
    n = len(waypoints)
    is_ccw = detect_track_direction(waypoints)
    prev_idx, next_idx = params["closest_waypoints"]
    x = float(params["x"]); y = float(params["y"])
    heading = float(params["heading"])
    width = float(params["track_width"])
    half_w = width * 0.5
    inv_half = 2.0 / width
    d2c = float(params["distance_from_center"])
    left = bool(params.get("is_left_of_center", True))
    speed = float(params.get("speed", 1.0))
    steer = float(params.get("steering_angle", 0.0))
    progress = float(params.get("progress", 0.0))
    steps = int(params.get("steps", 1) or 1)

    h0, d_near, curv_near = calculate_track_curvature(waypoints, prev_idx, next_idx, Config.LOOKAHEAD_CLOSE)
    _, d_far, curv_far = calculate_track_curvature(waypoints, prev_idx, next_idx, Config.LOOKAHEAD_FAR)
    turn_type = classify_turn_type(curv_near)
    compound = check_compound_turn(curv_near, curv_far)
    phase = determine_racing_phase(x, y, waypoints, next_idx, turn_type, curv_near)

    hdg_err = abs(normalize_angle(heading - h0))
    align = max(0.0, 1.0 - hdg_err * INV_90_DEGREES)

    steer_mag = abs(steer)
    max_steer = 30.0 + max(0, (2.5 - speed) * 5.0)
    smooth = max(0.1, 1.0 - steer_mag / max_steer)
    turn_norm = min(1.0, hdg_err * INV_30_DEGREES)
    stability = 0.7 * smooth + 0.3 * (1.0 - turn_norm)

    norm = d2c * inv_half
    signed_lat = norm if left else -norm

    total = 0.001

    if turn_type == 0:
        total *= 1.0 + 0.02 * (1.0 - min(norm, 1.0))

    total *= (0.6 + 0.4 * stability)
    total *= (0.5 + 0.5 * align)

    if (turn_type == 1 and phase != 2 and norm >= 0.60):
        left_turn = d_near > 0.0
        outside_left = not left_turn
        if left == outside_left:
            total *= Config.Bonus.ARC_OUTSIDE

    if turn_type == 0:
        sratio = min(speed * INV_MAX_SPEED, 1.0)
        total *= 1.16 + 0.64 * sratio
        stall_th = Config.STRAIGHT_STALL_THRESHOLD * Config.MAX_SPEED
        if speed < stall_th:
            total *= 0.80 + 0.20 * (speed / stall_th)
        weave_th = Config.BASE_WEAVE_THRESHOLD
        if speed < 2.5:
            weave_th += (2.5 - speed) * Config.LOW_SPEED_STEER_BONUS
        if steer_mag > weave_th:
            total *= Config.Bonus.WEAVING
        turns = analyze_upcoming_turns(waypoints, next_idx, Config.PRE_POSITION_HORIZON, Config.TURN_DETECTION_THRESHOLD)
        if turns and not is_chicane_ahead(turns, Config.CHICANE_DETECTION_WINDOW):
            steps_to, turn_dir, turn_mag = turns[0]
            if 3 <= steps_to <= Config.PRE_POSITION_HORIZON:
                turn_is_left = turn_dir > 0
                optimal_left = (not turn_is_left)
                if (left != optimal_left) and (norm <= 0.35):
                    total *= Config.Bonus.WRONG_SIDE_PENALTY
                elif (left == optimal_left) and (0.25 <= norm <= 0.75):
                    total *= Config.Bonus.OUTSIDE_STAGING + min(turn_mag / 90.0 * 0.03, 0.05)

    elif turn_type == 1:
        if curv_near > 4.2:
            total *= 0.93 + min(speed / Config.GENTLE_CORNER_SPEED, 0.45)
        else:
            total *= 0.95 + min(speed / Config.GENTLE_CORNER_SPEED, 0.55)

    else:
        if phase == 1:
            total *= 0.70 if speed > Config.SHARP_ENTRY_MAX_SPEED else 1.10
        elif phase == 2:
            total *= 0.75 + min(speed * INV_MAX_SPEED, 0.45)
        else:
            total *= 1.20 + min(speed / Config.SHARP_EXIT_TARGET_SPEED, 0.55)

    if phase > 0:
        if phase == 4:
            left_turn = d_near > 0.0
            tgt = -0.45 if left_turn else 0.45
            err = abs(signed_lat - tgt)
            acc = 1.0 - min(err / 0.35, 1.0) ** 2
            total *= (1.0 + 0.10 * acc)
            if speed > Config.GENTLE_CORNER_SPEED * 0.85:
                total *= 1.03
        elif phase == 1:
            tgt = (Config.LateralTarget.ENTRY_OUTSIDE if d_near <= 0 else -Config.LateralTarget.ENTRY_OUTSIDE)
            err = abs(signed_lat - tgt)
            acc = 1.0 - min(err / 0.30, 1.0) ** 2
            total *= (1.0 + Config.SMOOTHNESS_WEIGHT_OTHER * acc)
        elif phase == 2:
            tgt_val = Config.LateralTarget.APEX_INSIDE_SHARP
            tgt = (tgt_val if d_near > 0 else -tgt_val)
            err = abs(signed_lat - tgt)
            acc = 1.0 - min(err / 0.30, 1.0) ** 2
            total *= (1.0 + Config.SMOOTHNESS_WEIGHT_APEX * acc)
            lt = d_near > 0
            rng = (0.45, 0.75)
            if (left == lt) and (rng[0] <= norm <= rng[1]):
                total *= Config.Bonus.APEX_INSIDE_SHARP
            else:
                total *= Config.Bonus.APEX_WRONG_LINE
        elif phase == 3:
            tgt = (Config.LateralTarget.EXIT_OUTSIDE if d_near <= 0 else -Config.LateralTarget.EXIT_OUTSIDE)
            err = abs(signed_lat - tgt)
            acc = 1.0 - min(err / 0.30, 1.0) ** 2
            total *= (1.0 + Config.SMOOTHNESS_WEIGHT_OTHER * acc)

    margin = calculate_safety_margin(speed, curv_near, width)
    if norm > (1.0 - margin):
        danger = max(1.2, 1.0 + 2.5 * (speed * INV_MAX_SPEED) + 0.6 * curv_near)
        total *= max(0.03, 0.30 / danger)
    if norm > Config.STRICT_EDGE_THRESHOLD:
        total *= Config.Bonus.EDGE_PROXIMITY
    if norm > 0.88:
        back = (left and steer < -Config.STEER_BACK_THRESHOLD) or ((not left) and steer > Config.STEER_BACK_THRESHOLD)
        if back:
            total *= Config.Bonus.STEERING_BACK

    expected_progress = min(100.0, steps * INV_EXPECTED_STEPS * 100.0)
    if expected_progress > 0:
        pace_ratio = min(2.0, progress / expected_progress)
        total *= (1.0 + Config.PACE_WEIGHT_MAIN * (pace_ratio - 1.0))
        if progress > Config.LATE_RACE_THRESHOLD:
            total *= (1.0 + Config.PACE_WEIGHT_LATE * (pace_ratio - 1.0))
        if (progress >= Config.SPRINT_THRESHOLD) and (norm < 0.9):
            total *= (1.0 + Config.PACE_WEIGHT_SPRINT * (pace_ratio - 1.0))
        if progress >= Config.FINISH_LINE_THRESHOLD:
            time_saved = max(0.0, (EXPECTED_TOTAL_STEPS - steps) * INV_EXPECTED_STEPS)
            total *= (1.0 + 3.0 * time_saved)

    if compound and phase in [2, 3]:
        if speed > 2.5 and align > 0.7:
            total *= Config.Bonus.COMPOUND_TURN

    return max(0.001, float(total))
