classdef PianuraRobot < handle
    % PianuraRobot  Drive one Pianura robot from MATLAB (or GNU Octave) over UDP.
    %
    %   robot = PianuraRobot(1);                 % robot id 1, server 127.0.0.1:5555
    %   robot.connect();
    %   while true
    %       s = robot.sensors();                 % waits for the next control period
    %       if min(s.ir([1 2 18])) < 20          % ir(1) is sensor 0, the front one
    %           robot.drive(0, 1.5);             % obstacle ahead: turn on the spot
    %       else
    %           robot.drive(1, 0);               % straight on (cells/s, rad/s)
    %       end
    %   end
    %
    % Every call to sensors() must be followed by exactly one drive() or wheels(): in lockstep
    % mode the simulation waits for that answer before going on.
    %
    % Indices: MATLAB starts at 1, Pianura's sensors at 0. s.ir(1) is sensor 0 (front),
    % s.ir(k+1) is sensor k, clockwise every 20 degrees.
    %
    % Units: lengths in cells, time in seconds, angles in radians. The y axis grows downwards
    % and positive angles turn clockwise on screen.
    %
    % Errors (catch them with try/catch and look at err.identifier):
    %   Pianura:stopped   the simulation was stopped, or the robot was disabled
    %   Pianura:timeout   no message from Pianura within the timeout
    %   Pianura:error     Pianura answered with an error (unknown robot, bad command, ...)
    %   Pianura:protocol  the server speaks another protocol version
    %
    % It uses Java's java.net.DatagramSocket, which ships with MATLAB: no toolbox is needed.
    % It also runs in GNU Octave (7 or later) with Java support.

    properties (Constant)
        PROTOCOL_VERSION = 2
    end

    properties (SetAccess = private)
        RobotId          % id of the robot driven by this object
        Host             % address of the Pianura server
        Port             % UDP port of the Pianura server
        Timeout          % seconds to wait for a message before Pianura:timeout
        Welcome = []     % the welcome message (mode, control period, units), after connect()
    end

    properties (Access = private)
        Socket
        Address
        LastSeq = []
        Broadcasts = {}
        Sends = {}
    end

    methods
        function obj = PianuraRobot(robotId, host, port, timeout)
            % PianuraRobot(robotId, host = '127.0.0.1', port = 5555, timeout = 30)
            if nargin < 2 || isempty(host), host = '127.0.0.1'; end
            if nargin < 3 || isempty(port), port = 5555; end
            if nargin < 4 || isempty(timeout), timeout = 30; end
            obj.RobotId = robotId;
            obj.Host = host;
            obj.Port = port;
            obj.Timeout = timeout;
            obj.Socket = javaObject('java.net.DatagramSocket');
            obj.Socket.setSoTimeout(round(timeout * 1000));
            obj.Address = javaMethod('getByName', 'java.net.InetAddress', host);
        end

        function welcome = connect(obj)
            % Say hello and wait for the welcome message.
            obj.sendJson(struct('type', 'hello', 'robot', obj.RobotId));
            while true
                msg = obj.receiveJson();
                if strcmp(msg.type, 'welcome')
                    if ~isfield(msg, 'protocol') || msg.protocol ~= PianuraRobot.PROTOCOL_VERSION
                        error('Pianura:protocol', 'protocol %s not supported (this client speaks %d)', ...
                            num2str(PianuraRobot.fieldOr(msg, 'protocol', NaN)), PianuraRobot.PROTOCOL_VERSION);
                    end
                    obj.Welcome = msg;
                    welcome = msg;
                    return;
                end
            end
        end

        function s = sensors(obj)
            % Wait for the next sensor reading of this robot. Returns a struct:
            %   t, seq            simulated time (s) and sequence number
            %   x, y, theta       true pose (cells, cells, radians clockwise on screen)
            %   ir                1x18, distance in px from the body, 255 = nothing in range
            %   bump              1x18, 0 = no contact
            %   contact           true while the body is pushing against something
            %   floor             floor sensors, 0 = black .. 255 = white (1 = left, 2 = right)
            %   wheelLeft, wheelRight   distance travelled by each wheel (cells, signed)
            %   active            false after a simulated fault
            %   inbox             radio messages received since the previous reading:
            %                     struct array with sender, to (0 = broadcast), t, data
            %   raw               the message as received
            while true
                msg = obj.receiveJson();
                switch msg.type
                    case 'sensors'
                        obj.LastSeq = msg.seq;
                        s = struct();
                        s.t = msg.t;
                        s.seq = msg.seq;
                        s.x = msg.pose.x;
                        s.y = msg.pose.y;
                        s.theta = msg.pose.theta;
                        s.ir = PianuraRobot.row(msg.ir);
                        s.bump = PianuraRobot.row(msg.bump);
                        s.contact = logical(msg.contact);
                        s.floor = PianuraRobot.row(msg.floor);
                        s.wheelLeft = msg.wheels.left;
                        s.wheelRight = msg.wheels.right;
                        s.active = logical(msg.active);
                        s.inbox = PianuraRobot.parseInbox(PianuraRobot.fieldOr(msg, 'inbox', []));
                        s.raw = msg;
                        return;
                    case 'stopped'
                        error('Pianura:stopped', 'robot %d stopped', obj.RobotId);
                end
            end
        end

        function drive(obj, v, w)
            % drive(v, w): linear speed v (cells/s), angular speed w (rad/s, positive = clockwise).
            if nargin < 3, w = 0; end
            obj.command(struct('v', double(v), 'w', double(w)));
        end

        function wheels(obj, left, right)
            % wheels(left, right): wheel speeds, same units as RobotUnit.setMotorSpeed (500, 500 = straight on).
            obj.command(struct('left', double(left), 'right', double(right)));
        end

        function broadcast(obj, data)
            % broadcast(data): radio message to every robot in range; it leaves with the next drive()/wheels().
            jsonencode(data);                    % fail now if it cannot be sent as JSON
            obj.Broadcasts{end + 1} = data;
        end

        function send(obj, to, data)
            % send(to, data): radio message to robot `to` (delivered only if in range); leaves with the next drive()/wheels().
            jsonencode(data);
            obj.Sends{end + 1} = struct('to', double(to), 'data', {data});
        end

        function close(obj)
            % Stop driving the robot (it keeps its last command) and release the socket.
            if isempty(obj.Socket), return; end
            try
                obj.sendJson(struct('type', 'bye', 'robot', obj.RobotId));
            catch
            end
            obj.Socket.close();
            obj.Socket = [];
        end

        function delete(obj)
            obj.close();
        end
    end

    methods (Access = private)
        function command(obj, motion)
            msg = struct('type', 'cmd', 'robot', obj.RobotId);
            if ~isempty(obj.LastSeq), msg.seq = obj.LastSeq; end
            names = fieldnames(motion);
            for k = 1:numel(names)
                msg.(names{k}) = motion.(names{k});
            end
            % the first broadcast goes in "broadcast"; any further one becomes a "send" to "all"
            sends = {};
            if ~isempty(obj.Broadcasts)
                msg.broadcast = obj.Broadcasts{1};
                for k = 2:numel(obj.Broadcasts)
                    sends{end + 1} = struct('to', 'all', 'data', {obj.Broadcasts{k}}); %#ok<AGROW>
                end
            end
            sends = [sends, obj.Sends];
            if ~isempty(sends)
                msg.send = sends;                % a cell array is always encoded as a JSON array
            end
            obj.Broadcasts = {};
            obj.Sends = {};
            obj.sendJson(msg);
        end

        function sendJson(obj, msg)
            text = javaObject('java.lang.String', jsonencode(msg));
            bytes = text.getBytes('UTF-8');
            packet = javaObject('java.net.DatagramPacket', bytes, numel(bytes), obj.Address, obj.Port);
            obj.Socket.send(packet);
        end

        function msg = receiveJson(obj)
            packet = javaObject('java.net.DatagramPacket', zeros(1, 65535, 'int8'), 65535);
            try
                obj.Socket.receive(packet);
            catch err
                if ~isempty(strfind(err.message, 'SocketTimeoutException'))
                    error('Pianura:timeout', 'no message from Pianura in %g s: is the simulation running and the robot id right?', obj.Timeout);
                end
                rethrow(err);
            end
            text = char(javaObject('java.lang.String', packet.getData(), 0, packet.getLength(), 'UTF-8'));
            msg = jsondecode(text);
            if strcmp(msg.type, 'error')
                error('Pianura:error', '%s', PianuraRobot.fieldOr(msg, 'message', 'unknown error'));
            end
        end
    end

    methods (Static, Access = private)
        function v = row(x)
            v = double(x(:))';
        end

        function v = fieldOr(s, name, default)
            if isfield(s, name), v = s.(name); else, v = default; end
        end

        function inbox = parseInbox(raw)
            inbox = struct('sender', {}, 'to', {}, 't', {}, 'data', {});
            if isempty(raw), return; end
            if isstruct(raw), raw = num2cell(raw); end       % jsondecode gives a struct array or a cell array
            for k = 1:numel(raw)
                m = raw{k};
                if ischar(m.to), to = 0; else, to = double(m.to); end    % "all" = broadcast
                inbox(end + 1) = struct('sender', double(m.from), 'to', to, 't', m.t, 'data', {m.data}); %#ok<AGROW>
            end
        end
    end
end
