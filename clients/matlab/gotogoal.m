% Copyright (c) 2008-2026 Raffaele Grandi
% SPDX-License-Identifier: MIT

function rec = gotogoal(waypoints, robotId, host, port, doPlot)
% GOTOGOAL  Drive a robot through a list of waypoints with a proportional controller,
% then plot the trajectory and the errors.
%
%   1. run the Pianura example remote (./gradlew run -Pexample=remote): it starts paused
%   2. in MATLAB, from this folder:  rec = gotogoal();
%   3. press Run (or Space) in Pianura
%
%   rec = gotogoal(waypoints, robotId, host, port, doPlot)
%     waypoints  N x 2 matrix of [x y] in cells (default: a path around the obstacles of the
%                example remote)
%     robotId    robot to drive (default 1)
%     doPlot     plot the result at the end (default true)
%   rec is a struct of column vectors: t, x, y, theta, v, w, dist, headingError, waypoint.
%
% The controller, at every control period:
%   e     = heading error towards the current waypoint, wrapped to [-pi, pi]
%   w     = KW * e                                     (angular speed, rad/s)
%   v     = min(VMAX, KV * dist) * max(0, cos(e))      (linear speed, cells/s: slow down
%                                                       while the robot is not facing the goal)
% A waypoint counts as reached within TOL cells. It uses the true pose sent by Pianura.
% Try changing the gains, or replace the controller with your own.
%
% In Pianura the robot draws the route (layer 'route') and, at every step, an arrow to the
% current waypoint and the heading error (layer 'aim'): View -> Robot drawings. The Inspector
% (button on the toolbar) shows its state, the commands, and distance and heading error over time.

if nargin < 1 || isempty(waypoints), waypoints = [20 4; 33 4; 35 14]; end
if nargin < 2 || isempty(robotId), robotId = 1; end
if nargin < 3 || isempty(host), host = '127.0.0.1'; end
if nargin < 4 || isempty(port), port = 5555; end
if nargin < 5 || isempty(doPlot), doPlot = true; end

KV = 0.8;          % 1/s
KW = 2.0;          % 1/s
VMAX = 1.5;        % cells/s
TOL = 0.3;         % cells

robot = PianuraRobot(robotId, host, port);
w = robot.connect();
fprintf('robot %d: connected, mode=%s, period=%g s - press Run in Pianura\n', robotId, w.mode, w.period);

n = 0;
rec = struct('t', [], 'x', [], 'y', [], 'theta', [], 'v', [], 'w', [], 'dist', [], ...
             'headingError', [], 'waypoint', []);
k = 1;                                   % current waypoint
try
    while k <= size(waypoints, 1)
        s = robot.sensors();
        if n == 0                            % first reading: draw the route once (the layer keeps it)
            robot.drawClear('route');
            robot.drawPolyline('route', [s.x; waypoints(:, 1)], [s.y; waypoints(:, 2)], [0.6 0.6 0.6]);
            for j = 1:size(waypoints, 1)
                robot.drawCircle('route', waypoints(j, 1), waypoints(j, 2), TOL, 'r');
                robot.drawText('route', waypoints(j, 1) + 0.5, waypoints(j, 2) - 0.5, sprintf('%d', j), 'r');
            end
        end
        dx = waypoints(k, 1) - s.x;
        dy = waypoints(k, 2) - s.y;
        dist = hypot(dx, dy);
        if dist < TOL
            fprintf('t=%5.1f s  waypoint %d reached\n', s.t, k);
            robot.inspectLog(sprintf('waypoint %d reached', k));
            k = k + 1;
            robot.drive(0, 0);
            continue;
        end
        e = atan2(sin(atan2(dy, dx) - s.theta), cos(atan2(dy, dx) - s.theta));   % wrap to [-pi, pi]
        cmdW = KW * e;
        cmdV = min(VMAX, KV * dist) * max(0, cos(e));
        robot.drawClear('aim');              % redrawn at every step
        robot.drawArrow('aim', s.x, s.y, waypoints(k, 1), waypoints(k, 2), 'b');
        robot.drawText('aim', s.x + 1.2, s.y + 1.8, sprintf('e = %+.2f rad', e), 'b');
        robot.inspectState(sprintf('to waypoint %d', k));   % Pianura's Inspector window
        robot.inspectPlot('distance (cells)', dist);
        robot.inspectPlot('heading error (rad)', e);
        robot.inspectWatch('v (cells/s)', cmdV);
        robot.inspectWatch('w (rad/s)', cmdW);
        robot.drive(cmdV, cmdW);

        n = n + 1;
        rec.t(n, 1) = s.t;  rec.x(n, 1) = s.x;  rec.y(n, 1) = s.y;  rec.theta(n, 1) = s.theta;
        rec.v(n, 1) = cmdV; rec.w(n, 1) = cmdW; rec.dist(n, 1) = dist;
        rec.headingError(n, 1) = e;  rec.waypoint(n, 1) = k;
    end
    fprintf('all %d waypoints reached\n', size(waypoints, 1));
catch err
    if ~strcmp(err.identifier, 'Pianura:stopped'), robot.close(); rethrow(err); end
    fprintf('simulation stopped before the last waypoint\n');
end
robot.close();                           % the robot keeps its last command (stopped)

if doPlot && n > 0
    figure('Name', 'gotogoal');
    subplot(1, 2, 1);
    plot(rec.x, rec.y, 'b-', waypoints(:, 1), waypoints(:, 2), 'ro', rec.x(1), rec.y(1), 'ks');
    set(gca, 'YDir', 'reverse');         % as in Pianura: y grows downwards
    axis equal; grid on;
    xlabel('x (cells)'); ylabel('y (cells)'); title('Trajectory');
    legend('robot', 'waypoints', 'start', 'Location', 'northeast');
    subplot(1, 2, 2);
    plot(rec.t, rec.dist, 'b-', rec.t, rec.headingError, 'r-');
    grid on; xlabel('t (s)');
    legend('distance to waypoint (cells)', 'heading error (rad)', 'Location', 'northeast');
    title('Errors');
end
end
