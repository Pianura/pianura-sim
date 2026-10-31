function wander(ids, host, port)
% WANDER  Robots that wander around and steer away from obstacles, driven from MATLAB.
%
%   1. run the Pianura example remote (./gradlew run -Pexample=remote): it starts paused
%   2. in MATLAB, from this folder:  wander([1 2])
%   3. press Run (or Space) in Pianura
%
% Each robot also sends a radio beacon every second and prints which robots it can hear
% (neighbours are forgotten after 2.5 s of silence). The robots are served one after the
% other in the same loop: in lockstep mode Pianura waits for all of them at every period.
%
% This is only a minimal reactive behaviour to show the protocol; write your own!

if nargin < 1 || isempty(ids), ids = 1; end
if nargin < 2 || isempty(host), host = '127.0.0.1'; end
if nargin < 3 || isempty(port), port = 5555; end

p.front = [0 1 2 16 17] + 1;      % IR sensors around the heading (+1: MATLAB indices start at 1)
p.leftSide = (13:17) + 1;
p.rightSide = (1:5) + 1;
p.near = 18;                      % IR value (px from the body) under which an obstacle is "near"
p.beaconPeriod = 1.0;             % s between two radio beacons
p.forgetAfter = 2.5;              % s of silence after which a neighbour is forgotten

n = numel(ids);
robots = cell(1, n);
state = struct('id', num2cell(ids), 'turning', 0, 'turnDir', 1, 'nextBeacon', 0, ...
               'heardIds', [], 'heardAt', [], 'shown', [], 'seed', num2cell(ids), 't', 0);
try
    for k = 1:n
        robots{k} = PianuraRobot(ids(k), host, port);
        w = robots{k}.connect();
        fprintf('robot %d: connected, mode=%s, period=%g s\n', ids(k), w.mode, w.period);
    end
    while true
        for k = 1:n
            state(k) = step(robots{k}, state(k), p);
        end
    end
catch err
    closeAll(robots);
    if strcmp(err.identifier, 'Pianura:stopped')
        fprintf('simulation stopped at t=%.2f s\n', max([state.t]));
    else
        rethrow(err);
    end
end
end

function st = step(robot, st, p)
s = robot.sensors();
st.t = s.t;
% radio: who is around?
for m = 1:numel(s.inbox)
    j = find(st.heardIds == s.inbox(m).sender, 1);
    if isempty(j)
        st.heardIds(end + 1) = s.inbox(m).sender;
        st.heardAt(end + 1) = s.t;
    else
        st.heardAt(j) = s.t;
    end
end
keep = s.t - st.heardAt <= p.forgetAfter;
st.heardIds = st.heardIds(keep);
st.heardAt = st.heardAt(keep);
heard = sort(st.heardIds);
if ~isequal(heard, st.shown)
    st.shown = heard;
    if isempty(heard)
        fprintf('robot %d at t=%.1f s hears: nobody\n', st.id, s.t);
    else
        fprintf('robot %d at t=%.1f s hears: %s\n', st.id, s.t, mat2str(heard));
    end
end
if s.t >= st.nextBeacon - 1e-9
    robot.broadcast(struct('beacon', st.id, 'x', round(s.x * 100) / 100, 'y', round(s.y * 100) / 100));
    st.nextBeacon = st.nextBeacon + p.beaconPeriod;
end
% motion
if st.turning > 0
    st.turning = st.turning - 1;
    robot.drive(0, 2 * st.turnDir);
elseif min(s.ir(p.front)) < p.near || s.contact
    % turn towards the freer side (negative = counter-clockwise = to the left)
    if sum(s.ir(p.leftSide)) > sum(s.ir(p.rightSide)), st.turnDir = -1; else, st.turnDir = 1; end
    [r, st.seed] = nextRandom(st.seed);
    st.turning = 4 + floor(r * 7);
    robot.drive(0, 2 * st.turnDir);
else
    [r, st.seed] = nextRandom(st.seed);
    robot.drive(1.2, (r - 0.5) * 0.6);
end
end

function [r, seed] = nextRandom(seed)
% Park-Miller "minimal standard" generator: exact in double precision, the same in MATLAB
% and Octave, so in lockstep mode the same run gives the same result.
seed = mod(16807 * seed, 2147483647);
r = seed / 2147483647;
end

function closeAll(robots)
for k = 1:numel(robots)
    if ~isempty(robots{k}), robots{k}.close(); end
end
end
