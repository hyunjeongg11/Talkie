package com.e104.realtime.application;

import com.e104.realtime.common.exception.RestApiException;
import com.e104.realtime.common.status.StatusCode;
import com.e104.realtime.domain.ConversationAnalytics.*;
import com.e104.realtime.domain.DayAnalytics.DayAnalytics;
import com.e104.realtime.domain.User.Answer;
import com.e104.realtime.domain.User.ConversationContent;
import com.e104.realtime.domain.User.Question;
import com.e104.realtime.domain.User.User;
import com.e104.realtime.domain.WeekAnalytics.WeekAnalytics;
import com.e104.realtime.dto.*;
import com.e104.realtime.mqtt.OpenAISocketService;
import com.e104.realtime.redis.hash.Conversation;
import com.e104.realtime.redis.mapper.ConversationMapper;
import com.e104.realtime.redis.repository.ConversationRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;


@Service
@RequiredArgsConstructor
public class UserService {

    private final RepoUtil repoUtil;
    private final BuilderUtil builderUtil;
    private final ChatService chatService;
    private final ConversationRedisRepository conversationRedisRepository;

    private final OpenAISocketService openAISocketService;
    private final ConversationMapper conversationMapper;

    private final RestTemplate restTemplate;

    @Value("${fastapi.url}")
    private String fastApiUrl;

    // 로그인
    public LoginResponse login(String userId) {
        int userSeq = repoUtil.login(userId);
        User user = repoUtil.findUser(userSeq);
        return new LoginResponse(user.getUserSeq(), user.isNotFirstLogin());
    }

    // 유저정보 조회
    public UserResponse getUser(int userSeq) {
        User user = repoUtil.findUser(userSeq);
        return new UserResponse(user);
    }

    // 유저 정보 수정
    @Transactional
    public void updateUser(UserUpdateRequest request) {
        User user = repoUtil.findUser(request.getUserSeq());
        user.updateUserInfo(request.getName(), request.getAge(), request.getGender(), request.getFavorite());
        openAISocketService.sendSessionUpdate(user);
    }

    // 질문 등록
    @Transactional
    public void createQuestion(QuestionCreateRequest request) {
        User user = repoUtil.findUser(request.getUserSeq());
        Question question = builderUtil.buildQuestion(request.getContent());
        user.addQuestion(question);
    }

    // 질문 삭제
    @Transactional
    public void deleteQuestion(int userSeq) {
        User user = repoUtil.findUser(userSeq);
        user.removeQuestion();
    }

    // 질문 수정
    @Transactional
    public void updateQuestion(QuestionUpdateRequest request) {
        User user = repoUtil.findUser(request.getUserSeq());
        List<Question> questions = user.getQuestions();
        Question question = questions.get(questions.size() - 1);
        question.updateQuestion(request.getContent());
    }

    // 질문 및 응답 조회
    public List<QuestionAndResponseDto> getQuestionAndAnswerList(int userSeq) {
        User user = repoUtil.findUser(userSeq);
        List<Question> questions = user.getQuestions();

        return questions.stream()
                .map(QuestionAndResponseDto::new)
                .toList();
    }

    // 일자별 대화 목록 조회
    public ConversationListResponse getConversationList(int userSeq, LocalDate day) {
        User user = repoUtil.findUser(userSeq);
        return new ConversationListResponse(user, day);
    }

    // 대화 상세 조회
    public ConversationDetailResponse getConversationDetail(int userSeq, int conversationSeq) {
        User user = repoUtil.findUser(userSeq);
        List<ConversationContent> contents = user.getConversationContents();
        ConversationAnalytics analytics = user.getConversationAnalytics(conversationSeq);
        return new ConversationDetailResponse(analytics, contents);
    }

    // 대화 내용 요약 조회
    public ConversationSummaryResponse getConversationSummary(int userSeq, int conversationSeq) {
        User user = repoUtil.findUser(userSeq);
        ConversationAnalytics conversationAnalytics = user.getConversationAnalytics(conversationSeq);
        return new ConversationSummaryResponse(conversationAnalytics.getConversationSummary());
    }

    // 주별 대화 통계 조회
    public WeeklyConversationResponse getWeeklyConversation(int userSeq, LocalDate startDay, LocalDate endDay) {
        User user = repoUtil.findUser(userSeq);
        List<WeekAnalytics> weekAnalyticses = user.getWeekAnalytics();

        // 오늘이 몇년, 몇월, 몇주차 인지 찾기
        int startYear = startDay.getYear();
        int endYear = endDay.getYear();
        int startMonth = startDay.getMonthValue();
        int endMonth = endDay.getMonthValue();
        WeekFields weekFields = WeekFields.of(Locale.KOREA);
        int startWeek = startDay.get(weekFields.weekOfMonth());
        int endWeek = endDay.get(weekFields.weekOfMonth());
        if(startYear != endYear || startMonth != endMonth || startWeek != endWeek) {
            throw new RestApiException(StatusCode.BAD_REQUEST, "시작 날짜와 끝 날짜가 같은 년도,월,주차에 있어야 합니다.");
        }

        // 현재 연도, 월, 주차에 맞는 주간 대화 통계 조회
        WeekAnalytics weekAnalytics = weekAnalyticses.stream()
                .filter(w -> w.getYear() == endYear && w.getMonth() == endMonth && w.getWeek() == endWeek)
                .findFirst()
                .orElse(null);
        List<DayAnalytics> filteredDayAnalytics = user.getDayAnalytics().stream()
                .filter(d -> d.getCreatedAt().getYear() == endYear && d.getCreatedAt().getMonthValue() == endMonth && d.getCreatedAt().get(weekFields.weekOfMonth()) == endWeek)
                .toList();

        if (weekAnalytics == null) {
            throw new RestApiException(StatusCode.NO_CONTENT, "주간 대화 통계가 존재하지 않습니다.");
        }
        return new WeeklyConversationResponse(weekAnalytics, filteredDayAnalytics);
    }


    public void bufferConversation(Conversation conversation) {
        conversationRedisRepository.save(conversation);
    }

    @Transactional
    public void saveConversation(int userSeq) {
        List<Conversation> conversations = conversationRedisRepository.findAllByUserSeq(userSeq);
        // 아이가 한번도 대답하지 않음
        List<ConversationContent> conversationContents = conversations.stream().map(conversationMapper::toConversationContent).toList();

        // 대화 내용 저장할때 부모의 질문 활성화 되어있고, 아이의 대답이 완료되었다면 응답에도 저장해야함.
        User user = repoUtil.findUser(userSeq);
        List<Question> questions = user.getQuestions();
        Question question = questions.get(questions.size() - 1);
        boolean isActive = question.isActive();
        boolean isAnswered = question.isAnswered();
        if (isActive && isAnswered) {
            if(conversations.size() <= 1) {
                question.updateAnswerd(false);
                return;
            }
            String content = conversations.get(1).getContent(); // 아이의 제일 첫번째 대답을 뽑아내야함
            Answer answer = builderUtil.buildAnswer(content);
            question.addAnswer(answer);
        }
        if(conversations.size() <= 1) {
            return;
        }
        user.addConversationContents(conversationContents);

        // TODO: FAST API에서 감정분석, 워드클라우드, 어휘력 가져오기, DTO 생성 및 매핑
        fetchPostRequest(conversationContents);
        List<WordCloud> wordClouds = null;
        Vocabulary vocabulary = null;
        Sentiment sentiment = null;

        // 대화제목, 대화 내용 요약 가져오기
        String TitleAndContentSummary = chatService.getConversationSummary(conversationContents);
        String conversationContentSummary = null;
        String title = null;
        // "요약:"과 "제목:"을 기준으로 분리
        String[] parts = TitleAndContentSummary.split("제목:");
        if (parts.length == 2) {
            String[] summaryParts = parts[0].split("요약:");
            if (summaryParts.length == 2) {
                conversationContentSummary = summaryParts[1].trim(); // 요약 부분
            }
            title = parts[1].trim(); // 제목 부분
        }

        // 감정분석, 워드클라우드, 어휘력 설명 가져오기
        String wordCloudSummary = chatService.summarizeConversationWordCloud(wordClouds);
        String emotionSummary = chatService.summarizeConversationEmotion(sentiment);
        String vocabularySummary = chatService.summarizeConversationVocabulary(vocabulary, user.getAge());

        // 대화 통계 생성 및 저장
        ConversationAnalytics conversationAnalytics = builderUtil.buildConversationAnalytics(title, emotionSummary, vocabularySummary, wordCloudSummary);
        ConversationSummary conversationSummary = builderUtil.buildConversationSummary(conversationContentSummary);
        conversationAnalytics.addConversationSummary(conversationSummary);
        conversationAnalytics.addSentiment(sentiment);
        conversationAnalytics.addVocabulary(vocabulary);
        conversationAnalytics.addWordCloud(wordClouds);
        user.addConversationAnalytics(conversationAnalytics);

        // Redis 대화 삭제
        conversationRedisRepository.deleteAllByUserSeq(userSeq);
    }

    private void fetchPostRequest(List<ConversationContent> conversationContents) {
        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 요청 데이터 생성
        MultiValueMap<String, List<ConversationContent>> requestData = new LinkedMultiValueMap<>();
        requestData.add("Conversation", conversationContents);

        // HTTP 엔티티 생성 (헤더와 데이터를 함께 설정)
        HttpEntity<MultiValueMap<String, List<ConversationContent>>> requestEntity = new HttpEntity<>(requestData, headers);
        // HTTP POST 요청 보내기
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(fastApiUrl, requestEntity, String.class);

        // 응답 값
        String responseBody = responseEntity.getBody();
        System.out.println("POST Response: " + responseBody);
    }
}