package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.repo.ReceiptJpaRepository;
import com.clearing.netting.domain.model.ReceiptEntry;
import com.clearing.netting.domain.port.out.ReceiptRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ReceiptRepositoryAdapter implements ReceiptRepositoryPort {

    private final ReceiptJpaRepository repository;

    public ReceiptRepositoryAdapter(ReceiptJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ReceiptEntry save(ReceiptEntry receipt) {
        return PersistenceMapper.toDomain(repository.save(PersistenceMapper.toEntity(receipt)));
    }

    @Override
    public Optional<ReceiptEntry> findByRunIdAndMemberId(String runId, String memberId) {
        return repository.findByRunIdAndMemberId(runId, memberId).map(PersistenceMapper::toDomain);
    }

    @Override
    public List<ReceiptEntry> findByRunId(String runId) {
        return repository.findByRunId(runId).stream()
                .map(PersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByRunIdAndMemberId(String runId, String memberId) {
        repository.deleteByRunIdAndMemberId(runId, memberId);
    }
}
