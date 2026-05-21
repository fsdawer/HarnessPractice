#!/bin/bash
cd ~/Desktop/Study/beauty
mkdir -p .claude/logs

# 로그 파일 미리 생성
for agent in planner backend frontend test review architecture qa ux notion; do
  touch .claude/logs/$agent.log
done

SESSION="agents"
tmux kill-session -t $SESSION 2>/dev/null
tmux new-session -d -s $SESSION -x 220 -y 50

# 화면 분할
tmux split-window -h -t $SESSION
tmux split-window -v -t $SESSION:0.0
tmux split-window -v -t $SESSION:0.1
tmux split-window -v -t $SESSION:0.0
tmux split-window -v -t $SESSION:0.2

# 각 패널에 tail
tmux send-keys -t $SESSION:0.0 'tail -f .claude/logs/planner.log | sed "s/^/🧠 PLANNER      | /"' Enter
tmux send-keys -t $SESSION:0.1 'tail -f .claude/logs/backend.log | sed "s/^/⚙️  BACKEND      | /"' Enter
tmux send-keys -t $SESSION:0.2 'tail -f .claude/logs/frontend.log | sed "s/^/🎨 FRONTEND     | /"' Enter
tmux send-keys -t $SESSION:0.3 'tail -f .claude/logs/review.log | sed "s/^/🔍 REVIEW       | /"' Enter
tmux send-keys -t $SESSION:0.4 'tail -f .claude/logs/qa.log | sed "s/^/🧪 QA           | /"' Enter

tmux attach -t $SESSION
