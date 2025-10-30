#!/bin/bash
# 각 Lambda를 순차적으로 테스트하는 간단한 스크립트

echo "======================================================================"
echo "  UniSync Lambda 단위 테스트"
echo "======================================================================"
echo ""

# Canvas Sync Lambda 테스트
echo "🧪 Canvas Sync Lambda 테스트..."
python -m pytest app/serverless/canvas-sync-lambda/tests -v
CANVAS_RESULT=$?

echo ""
echo "----------------------------------------------------------------------"
echo ""

# LLM Lambda 테스트
echo "🧪 LLM Lambda 테스트..."
python -m pytest app/serverless/llm-lambda/tests -v
LLM_RESULT=$?

echo ""
echo "======================================================================"

# 결과 요약
if [ $CANVAS_RESULT -eq 0 ] && [ $LLM_RESULT -eq 0 ]; then
    echo "✅ 모든 테스트 통과!"
    exit 0
else
    echo "❌ 일부 테스트 실패"
    [ $CANVAS_RESULT -ne 0 ] && echo "  - Canvas Sync Lambda 실패"
    [ $LLM_RESULT -ne 0 ] && echo "  - LLM Lambda 실패"
    exit 1
fi

