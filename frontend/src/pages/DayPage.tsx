import React, { useState, useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { FaRegCalendarAlt } from "react-icons/fa";
import Calendar from "../components/Calendar";
import moment from "moment";
import "../index.css";
import { getConversationListByDate } from "../apis/api";
import { ConversationItem } from "../type";
import useUserStore from "../store/useUserStore";

const DayPage: React.FC = () => {
  const navigate = useNavigate();
  const { date } = useParams<{ date: string }>();
  const [showCalendar, setShowCalendar] = useState(false);
  const [stories, setStories] = useState<ConversationItem[]>([]);
  const { userSeq } = useUserStore();

  const toggleCalendar = () => {
    setShowCalendar(!showCalendar);
  };


  useEffect(() => {
    const fetchConversations = async () => {
      if (userSeq && date) {
        try {
          const response = await getConversationListByDate(userSeq, date);
          const conversations = response.data.conversationList.map(
            (item: ConversationItem, index: number) => ({
              ...item,
              order: getOrderText(index + 1),
              formattedTime: formatTime(item.createdAt),
            })
          );
          setStories(conversations);
        } catch (error) {
          console.error("대화 목록을 가져오는 중 오류 발생:", error);
        }
      }
    };

    fetchConversations();
  }, [userSeq, date]);

  const getOrderText = (order: number): string => {
    const units = [
      "",
      "한",
      "두",
      "세",
      "네",
      "다섯",
      "여섯",
      "일곱",
      "여덟",
      "아홉",
    ];
    const tens = [
      "",
      "열",
      "스물",
      "서른",
      "마흔",
      "쉰",
      "예순",
      "일흔",
      "여든",
      "아흔",
    ];

    if (order <= 0) return "";
    if (order > 99) return `${order}번째`;

    if (order === 1) return "첫번째";

    const ten = Math.floor(order / 10);
    const unit = order % 10;

    if (unit === 0) return `${tens[ten]}번째`;

    if (unit === 1 && ten > 0) return `${tens[ten]}한번째`;

    return `${tens[ten]}${units[unit]}번째`;
  };

  const formatTime = (createdAt: number[]) => {
    if (createdAt.length >= 6) {
      const [year, month, day, hours, minutes] = createdAt;
      const date = new Date(year, month - 1, day, hours, minutes);
      const formattedHours = String(date.getHours()).padStart(2, "0");
      const formattedMinutes = String(date.getMinutes()).padStart(2, "0");
      return `${formattedHours}시 ${formattedMinutes}분`;
    }
    return "";
  };

  // 날짜를 "yyyy년 mm월 dd일" 형식으로 포맷
  const formattedDate = date ? moment(date).format("YYYY년 MM월 DD일") : "";

  return (
    <div className="relative flex flex-col items-center min-h-screen">
      {/* 구름과 날짜 */}
      <div className="flex items-center justify-center mt-16 relative animate-float">
        <img src="/assets/cloud.png" alt="cloud" className="w-80" />
        <div
          onClick={toggleCalendar}
          className="absolute inset-0 flex items-center justify-center text-[26px] font-bold text-gray-700 mt-3 cursor-pointer"
        >
          <FaRegCalendarAlt className="cursor-pointer mr-2" />
          <span>{formattedDate}</span>
        </div>
      </div>

      {/* 이야기 리스트 */}
      <div className="w-4/5 mt-12 bg-[#D9D9D9] bg-opacity-60 rounded-xl shadow-lg h-[650px] overflow-y-scroll py-16 px-10">
        {stories.length > 0 ? (
          <div className="grid grid-cols-2 gap-y-20 gap-x-10">
            {stories.map((story) => (
              <div key={story.conversationSeq} className="relative">
                {/* 왼쪽 상단의 순서 */}
                <span className="absolute -top-10 left-0 text-neutral-800 font-bold text-xl ml-2">
                  {story.order} 이야기
                </span>

                {/* 이야기에 관한 div */}
                <div
                  className="flex items-center p-4 bg-white rounded-2xl shadow-md cursor-pointer hover:scale-105 transition-transform duration-200"
                  onClick={() =>
                    navigate(`/talk/${date}/${story.conversationSeq}`, {
                      state: { title: story.title, order: story.order },
                    })
                  }
                >
                  {/* 동물 이미지 */}
                  <img
                    src={`/assets/animals/animal${
                      (story.conversationSeq % 3) + 1
                    }.png`}
                    alt="animal"
                    className="w-40 rounded-3xl px-10 py-3 bg-[#dadbe9] mr-4"
                  />

                  {/* 이야기 제목과 시간 */}
                  <div className="flex flex-col flex-1 ml-4 mb-3">
                    <span className="font-semibold text-2xl mb-3">
                      {story.title.replace(/^"|"$/g, "")}
                    </span>
                    <span className="text-[#707070]">
                      {story.formattedTime}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center text-gray-700 text-2xl font-semibold py-16">
            이 날은 대화 목록이 없어요!
          </div>
        )}
      </div>

      {/* 달력 모달 */}
      {showCalendar && <Calendar onClose={() => setShowCalendar(false)} />}
    </div>
  );
};

export default DayPage;
