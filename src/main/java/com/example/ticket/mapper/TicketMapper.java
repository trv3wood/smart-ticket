package com.example.ticket.mapper;

import com.example.ticket.domain.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface TicketMapper {
    @Insert("""
        INSERT INTO ticket(title,description,category,priority,status,created_by,version)
        VALUES(#{title},#{description},#{category},#{priority},#{status},#{createdBy},0)
        """)
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insert(Ticket ticket);

    @Select("SELECT * FROM ticket WHERE id=#{id}")
    Ticket findById(long id);

    @Select("""
        <script>
        SELECT id,title,category,priority,status,created_by,version,created_at,updated_at
        FROM ticket
        <where>
          <if test="status != null">status=#{status}</if>
          <if test="beforeId != null">AND id &lt; #{beforeId}</if>
        </where>
        ORDER BY id DESC LIMIT #{limit}
        </script>
        """)
    List<Ticket> page(@Param("status") Status status, @Param("beforeId") Long beforeId,
                      @Param("limit") int limit);

    @Update("""
        UPDATE ticket SET status=#{target},version=version+1,updated_at=CURRENT_TIMESTAMP
        WHERE id=#{id} AND version=#{version} AND status=#{source}
        """)
    int transition(@Param("id") long id, @Param("version") int version,
                   @Param("source") Status source, @Param("target") Status target);

    @Insert("""
        INSERT INTO ticket_event(ticket_id,from_status,to_status,actor,note)
        VALUES(#{ticketId},#{source},#{target},#{actor},#{note})
        """)
    int addEvent(@Param("ticketId") long ticketId, @Param("source") String source,
                 @Param("target") String target, @Param("actor") String actor,
                 @Param("note") String note);

    @Select("SELECT * FROM ticket_event WHERE ticket_id=#{id} ORDER BY id LIMIT 100")
    List<TicketEvent> events(long id);
}
