package com.group.carbon.model;

import com.group.carbon.common.Types.BatchStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "source_batches")
public class SourceBatch {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "batch_no", nullable = false, unique = true)
  private String batchNo;
  @Column(name = "plant_id", nullable = false)
  private Long plantId;
  @Column(nullable = false)
  private String category;          // FUEL / ELECTRICITY / PROCUREMENT
  @Column(nullable = false)
  private String period;            // YYYY-MM
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BatchStatus status;
  @Column(name = "uploaded_by", nullable = false)
  private String uploadedBy;
  @Column(name = "uploaded_at", nullable = false)
  private LocalDateTime uploadedAt = LocalDateTime.now();
  @Column(name = "verified_by")
  private String verifiedBy;
  @Column(name = "verified_at")
  private LocalDateTime verifiedAt;
  @Column(name = "replaces_batch_id")
  private Long replacesBatchId;
  @Column(name = "revision_no", nullable = false)
  private int revisionNo = 0;
  @Column(name = "void_reason")
  private String voidReason;
  @Column(name = "row_count", nullable = false)
  private int rowCount = 0;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getBatchNo() { return batchNo; }
  public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public String getPeriod() { return period; }
  public void setPeriod(String period) { this.period = period; }
  public BatchStatus getStatus() { return status; }
  public void setStatus(BatchStatus status) { this.status = status; }
  public String getUploadedBy() { return uploadedBy; }
  public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
  public LocalDateTime getUploadedAt() { return uploadedAt; }
  public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
  public String getVerifiedBy() { return verifiedBy; }
  public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
  public LocalDateTime getVerifiedAt() { return verifiedAt; }
  public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
  public Long getReplacesBatchId() { return replacesBatchId; }
  public void setReplacesBatchId(Long replacesBatchId) { this.replacesBatchId = replacesBatchId; }
  public int getRevisionNo() { return revisionNo; }
  public void setRevisionNo(int revisionNo) { this.revisionNo = revisionNo; }
  public String getVoidReason() { return voidReason; }
  public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
  public int getRowCount() { return rowCount; }
  public void setRowCount(int rowCount) { this.rowCount = rowCount; }
}
