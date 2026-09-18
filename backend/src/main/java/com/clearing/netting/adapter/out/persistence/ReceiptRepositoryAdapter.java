package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.repo.ReceiptRecordJpaRepository;
import com.clearing.netting.domain.model.ReceiptRecord;
import com.clearing.netting.domain.port.out.ReceiptRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ReceiptRepositoryAdapter implements ReceiptRepositoryPort {

    private final ReceiptRecordJpaRepository repository;

    public ReceiptRepositoryAdapter(ReceiptRecordJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ReceiptRecord save(ReceiptRecord receipt) {
        return PersistenceMapper.toDomain(repository.save(PersistenceMapper.toEntity(receipt)));
    }

    @Override
    public List<ReceiptRecord> findByRunId(String runId) {
        return repository.findByRunId(runId).stream()
                .map(PersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ReceiptRecord> findByRunIdAndMemberId(String runId, String memberId) {
        return repository.findByRunIdAndMemberId(runId, memberId).map(PersistenceMapper::toDomain);
    }
}
