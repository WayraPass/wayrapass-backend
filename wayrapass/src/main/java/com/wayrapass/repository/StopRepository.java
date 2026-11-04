package com.wayrapass.repository;
import com.wayrapass.model.Stop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface StopRepository extends JpaRepository<Stop,Long>{
 @Query("""
  select distinct s.district from Stop s
  where s.active=true and s.district is not null
   and trim(s.district) <> ''
   and not exists (select rs.id from RouteStop rs where rs.stop=s and rs.active=true
                   and rs.stopOrder=(select max(rs2.stopOrder) from RouteStop rs2
                                     where rs2.route.id=rs.route.id and rs2.active=true))
  order by s.district
  """)
 List<String> findAvailablePickupDistricts();

 @Query("""
  select distinct s from Stop s
  where s.active=true
   and lower(s.district)=lower(:district)
   and not exists (select rs.id from RouteStop rs where rs.stop=s and rs.active=true
                   and rs.stopOrder=(select max(rs2.stopOrder) from RouteStop rs2
                                     where rs2.route.id=rs.route.id and rs2.active=true))
  order by s.name,s.id
  """)
 List<Stop> findAvailablePickupsByDistrict(@Param("district") String district);
}
