package com.cinema.chat.tool;

import com.cinema.dto.PageResult;
import com.cinema.dto.SessionVO;
import com.cinema.service.ISessionService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 工具：查场次（只读）
 */
@Component
@RequiredArgsConstructor
public class SessionQueryTool {

    private final ISessionService sessionService;

    @Tool("查询正在售票的电影场次列表。三个参数都可省略不传：movieId=电影编号, cinemaId=影院编号, date=日期(yyyy-MM-dd)。用户说'有哪些场次/最近有什么电影放/在放什么'时直接不传参数调用，不要反问用户。返回最近售票中场次的片名/影院/影厅/开场时间/票价/是否热门。")
    public String searchSessions(Long movieId, Long cinemaId, String date) {
        PageResult<SessionVO> page = sessionService.pageSessions(movieId, cinemaId, date, 1, 10);
        if (page.getRecords() == null || page.getRecords().isEmpty()) {
            return "没有找到符合条件的场次。";
        }
        StringBuilder sb = new StringBuilder("找到以下场次：\n");
        for (SessionVO v : page.getRecords()) {
            sb.append("场次id=").append(v.getId())
                    .append("，《").append(v.getMovieTitle()).append("》 ")
                    .append(v.getCinemaName()).append("·").append(v.getHallName())
                    .append(" 开场").append(v.getStartTime())
                    .append(" 票价").append(v.getPrice()).append("元")
                    .append(Boolean.TRUE.equals(v.getIsHot()) ? " [热门需抢票]" : "")
                    .append("\n");
        }
        return sb.toString();
    }
}
