/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.repository;

import alfio.model.*;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@QueryRepository
public interface TicketFieldRepository {

    @Query("select count(*) from ticket_field_value where ticket_id_fk = :ticketId")
    Integer optionalDataCount(@Bind("ticketId") int id);

    @Query("select ticket_id_fk, ticket_field_configuration_id_fk, field_name, field_value from ticket_field_value inner join ticket_field_configuration on id = ticket_field_configuration_id_fk where ticket_id_fk = :ticketId")
    List<TicketFieldValue> findAllByTicketId(@Bind("ticketId") int id);

    @Query("update ticket_field_value set field_value = :value where ticket_id_fk = :ticketId and ticket_field_configuration_id_fk = :fieldConfigurationId")
    int update(@Bind("ticketId") int ticketId, @Bind("fieldConfigurationId") int fieldConfigurationId, @Bind("value") String value);

    @Query("insert into ticket_field_value(ticket_id_fk, ticket_field_configuration_id_fk, field_value) values (:ticketId, :fieldConfigurationId, :value)")
    int insert(@Bind("ticketId") int ticketId, @Bind("fieldConfigurationId") int fieldConfigurationId, @Bind("value") String value);

    @Query("delete from ticket_field_value where ticket_id_fk = :ticketId and ticket_field_configuration_id_fk = :fieldConfigurationId")
    int delete(@Bind("ticketId") int ticketId, @Bind("fieldConfigurationId") int fieldConfigurationId);

    @Query("select ticket_field_configuration_id_fk, description from ticket_field_description  inner join ticket_field_configuration on ticket_field_configuration_id_fk = id where field_locale = :locale and event_id_fk = :eventId")
    List<TicketFieldDescription> findDescriptions(@Bind("eventId") int eventId, @Bind("locale") String locale);

    @Query("SELECT field_name FROM TICKET_FIELD_CONFIGURATION inner join event on event.id = event_id_fk  where short_name = :eventShortName order by field_order asc ")
    List<String> findFieldsForEvent(@Bind("eventShortName") String eventShortName);

    @Query("select field_name, field_value from ticket_field_value inner join ticket_field_configuration on ticket_field_configuration_id_fk = id where ticket_id_fk = :ticketId")
    List<TicketFieldNameAndValue> findNameAndValue(@Bind("ticketId") int ticketId);

    default void updateOrInsert(Map<String, String> values, Ticket ticket, Event event) {
        int ticketId = ticket.getId();
        int eventId = event.getId();
        Map<String, TicketFieldValue> toUpdate = findAllByTicketIdGroupedByName(ticketId);
        values = Optional.ofNullable(values).orElseGet(Collections::emptyMap);
        Map<String, Integer> fieldNameToId = findAdditionalFieldsForEvent(eventId).stream().collect(Collectors.toMap(TicketFieldConfiguration::getName, TicketFieldConfiguration::getId));

        values.forEach((fieldName, fieldValue) -> {
            boolean isNotBlank = StringUtils.isNotBlank(fieldValue);
            if(toUpdate.containsKey(fieldName)) {
                TicketFieldValue field = toUpdate.get(fieldName);
                if(isNotBlank) {
                    update(field.getTicketId(), field.getTicketFieldConfigurationId(), fieldValue);
                } else {
                    delete(field.getTicketId(), field.getTicketFieldConfigurationId());
                }
            } else if(fieldNameToId.containsKey(fieldName) && isNotBlank) {
                insert(ticketId, fieldNameToId.get(fieldName), fieldValue);
            }
        });
    }

    default Map<String, TicketFieldValue> findAllByTicketIdGroupedByName(int id) {
        return findAllByTicketId(id).stream().collect(Collectors.toMap(TicketFieldValue::getName, Function.identity()));
    }

    default boolean hasOptionalData(int ticketId) {
        return optionalDataCount(ticketId) > 0;
    }


    @Query("select * from TICKET_FIELD_CONFIGURATION where event_id_fk = :eventId order by field_order asc")
    List<TicketFieldConfiguration> findAdditionalFieldsForEvent(@Bind("eventId") int eventId);

    default Map<Integer, TicketFieldDescription> findTranslationsFor(Locale locale, int eventId) {
        return findDescriptions(eventId, locale.getLanguage()).stream().collect(Collectors.toMap(TicketFieldDescription::getTicketFieldConfigurationId, Function.identity()));
    }

    default Map<String, String> findAllValuesForTicketId(int ticketId) {
        return findNameAndValue(ticketId).stream().collect(Collectors.toMap(TicketFieldNameAndValue::getName, TicketFieldNameAndValue::getValue));
    }
}
