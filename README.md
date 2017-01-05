# TWS Position Sizer

This is a utility that will read in information from Interactive Brokers Trader Workstation and calculate a position size based on a max
risk percent and a stop loss that you enter. Currently only works with equities and single accounts. Pre-alpha, use at your own risk.
Nothing contained here within or any output of the program constitutes investment advice.

If you're having trouble connecting, make sure you have checked the box labeled "Enable ActiveX and Socket Clients" in File > Global Settings > API > Settings,
and make sure the socket port numbers in TWS and the position sizer app match.
