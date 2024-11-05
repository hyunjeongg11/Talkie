# main.py
import asyncio
# from src.sensors.pir_sensor_controll import detect_child_approach
# from src.stt.stt_handler import start_conversation
from src.wake_word.talkie_wake_word import initialize_wake_word, detect_wake_word

# 대화 상태 플래그
conversation_active = False

# 대화 시작 함수
async def initiate_conversation():
    global conversation_active
    conversation_active = True
    print("대화를 시작합니다...")
    # 실제 대화 로직 호출
    # await start_conversation()
    await asyncio.sleep(10)
    conversation_active = False

# PIR 센서 감지
async def detect_motion():
    global conversation_active
    while True:
        if conversation_active is not True:
            print("a")
        # if not conversation_active and detect_child_approach():
        #     print("PIR 센서로 감지됨! 대화를 시작합니다.")
        #     await initiate_conversation()
        await asyncio.sleep(1)

# 웨이크 워드 감지
async def check_wake_word():
    global conversation_active
    porcupine, stream = initialize_wake_word()  # 초기화는 한 번만
    try:
        while True:
            if not conversation_active and await detect_wake_word(porcupine, stream):
                print("웨이크 워드 감지됨! 대화를 시작합니다.")
                await initiate_conversation()
            await asyncio.sleep(0.01)
    finally:
        stream.stop_stream()
        stream.close()
        porcupine.delete()

# 메인 함수
async def main():
    await asyncio.gather(
        detect_motion(),
        check_wake_word()
    )

if __name__ == "__main__":
    asyncio.run(main())
