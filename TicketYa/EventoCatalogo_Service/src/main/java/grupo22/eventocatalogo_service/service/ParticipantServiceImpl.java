package grupo22.eventocatalogo_service.service;

import grupo22.eventocatalogo_service.DTO.request.ParticipantRequestDTO;
import grupo22.eventocatalogo_service.DTO.response.ParticipantResponseDTO;
import grupo22.eventocatalogo_service.domain.Participant;
import grupo22.eventocatalogo_service.exception.ResourceNotFoundException;
import grupo22.eventocatalogo_service.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ParticipantServiceImpl implements ParticipantService {

    private final grupo22.eventocatalogo_service.cache.PublicCatalogueCache catalogueCache;
    private final ParticipantRepository participantRepository;

    public ParticipantServiceImpl(ParticipantRepository participantRepository,grupo22.eventocatalogo_service.cache.PublicCatalogueCache catalogueCache) {
        this.catalogueCache=catalogueCache;
        this.participantRepository = participantRepository;
    }

    @Override
    @Transactional
    public ParticipantResponseDTO createParticipant(ParticipantRequestDTO request) {
        Participant participant = new Participant(request.getName(), request.getRole());
        Participant saved = participantRepository.save(participant);
        catalogueCache.invalidateAfterCommit();
        return new ParticipantResponseDTO(saved.getParticipantId(), saved.getName(), saved.getRole());
    }

    @Override
    public List<ParticipantResponseDTO> getAllParticipants() {
        return catalogueCache.all().stream().map(p->new ParticipantResponseDTO(p.participantId(),p.name(),p.role())).toList();
    }

    @Override
    public ParticipantResponseDTO getParticipantById(Long id) {
        var participant=catalogueCache.find(id);
        return new ParticipantResponseDTO(participant.participantId(),participant.name(),participant.role());
    }

    @Override
    @Transactional
    public ParticipantResponseDTO updateParticipant(Long id, ParticipantRequestDTO request) {
        Participant participant = findEntityById(id);
        participant.setName(request.getName());
        participant.setRole(request.getRole());
        Participant updated = participantRepository.save(participant);
        catalogueCache.invalidateAfterCommit();
        return mapToDTO(updated);
    }

    @Override
    @Transactional
    public void deleteParticipant(Long id) {
        Participant participant = findEntityById(id);
        participantRepository.delete(participant);
        catalogueCache.invalidateAfterCommit();
    }

    private Participant findEntityById(Long id) {
        return participantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Participante no encontrado con el ID: " + id));
    }

    private ParticipantResponseDTO mapToDTO(Participant participant) {
        return new ParticipantResponseDTO(
                participant.getParticipantId(),
                participant.getName(),
                participant.getRole()
        );
    }
}