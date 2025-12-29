# Schema Inference Design: Custom vs AWS Glue Crawlers

**Status:** Implemented (Custom)
**Date:** 2025-12-29
**Decision:** Keep custom schema inference implementation

## Context

DataSpray uses AWS Glue Data Catalog and Schema Registry to enable Athena queries over data stored in S3. When topics are created with retention (batch storage), schemas must be created to define the table structure for querying.

This document analyzes two approaches for schema inference:
1. **Custom implementation** - Current solution that samples S3 files
2. **AWS Glue Crawlers** - AWS-native schema detection service

## Current Implementation

### How It Works

**Location:** `dataspray-store/src/main/java/io/dataspray/store/impl/FirehoseS3AthenaBatchStore.java:470-569`

The custom schema inference process:
1. Triggered manually via UI "Recalculate Schema" button
2. Lists S3 objects in the topic's retention-specific prefix
3. Reads up to 10 sample files from S3
4. Parses JSON line-by-line to collect all field names
5. Creates simple JSON schema (all fields typed as "string")
6. Updates Glue Data Catalog table with schema
7. Registers schema version in Glue Schema Registry

**IAM Permissions:** Uses only Data Catalog and Schema Registry APIs (no crawler permissions)

**Cost:** ~$0.005 per execution
- Lambda execution time for reading samples
- S3 GET requests (10 files)
- Minimal data transfer

**Speed:** Seconds (immediate execution)

**User Experience:** Click button → immediate feedback

## AWS Glue Crawler Alternative

### Capabilities

AWS Glue Crawlers provide:
- **Sophisticated type inference** - Detects int, double, timestamp, boolean, etc.
- **Automatic partition discovery** - Finds and updates partitions
- **Multi-format support** - JSON, Parquet, CSV, Avro, ORC
- **Schema evolution handling** - Detects and versions schema changes
- **Built-in classifiers** - Handles various data formats automatically

### How Crawlers Work

1. Connect to S3 data store
2. Scan data files using AWS classifiers
3. Infer schemas with proper data types
4. Extract statistics about data
5. Populate Glue Data Catalog automatically
6. Can run on schedule or on-demand

### Cost Analysis

**AWS Glue Crawler Pricing:**
- **$0.44 per DPU-hour**
- 1 DPU = 4 vCPU + 16 GB memory
- Billed per second, rounded up (1-minute minimum per run)
- Some sources indicate 10-minute minimum duration

**Example Scenarios:**

| Scenario | Duration | DPUs | Cost per Run |
|----------|----------|------|--------------|
| Small crawl (few files) | 5 min | 2 | $0.07 |
| Medium crawl | 15 min | 2 | $0.22 |
| Large crawl | 30 min | 2 | $0.44 |

**Monthly Cost (30 runs):**
- Custom implementation: ~$0.15/month
- AWS Glue Crawlers: ~$2.10/month (assuming 5-min runs)
- **14x cost difference**

**Data Catalog Costs:** (Same for both approaches)
- First 1M objects + 1M requests: FREE
- Beyond free tier: $1 per 100K objects/month

## Trade-off Analysis

### Advantages of AWS Glue Crawlers

✅ **Better Type Inference**
- Detects actual data types (int, double, timestamp, boolean)
- Current custom: everything is "string"
- Better query performance with proper types
- Users don't need `CAST()` in Athena queries

✅ **Less Code to Maintain**
- No custom parsing logic needed
- AWS handles edge cases and format variations
- Automatic updates as AWS improves classifiers
- Reduced maintenance burden

✅ **Automatic Partition Discovery**
- Crawlers automatically detect new partitions
- Current: partitions must be manually managed or pre-configured

✅ **Schema Evolution Handling**
- Automatically detects schema changes
- Creates new versions in Data Catalog
- Handles backward compatibility

✅ **Multi-Format Support**
- Ready for Parquet, Avro, CSV in the future
- Current: JSON-only parsing

### Advantages of Custom Implementation

✅ **Lower Cost**
- ~14x cheaper for regular usage
- Scales better with many topics/organizations
- No per-run DPU charges

✅ **Faster Execution**
- Lambda execution in seconds
- Crawlers have 1-10 minute minimum runtime
- Immediate user feedback

✅ **Full Control**
- Precise control over sampling strategy
- Custom logic for DataSpray-specific needs
- Can optimize for specific use cases
- No crawler scheduling delays

✅ **Simpler Security Model**
- No IAM crawler permissions needed
- Fewer AWS service dependencies
- Reduced attack surface

✅ **Already Working**
- Implemented and tested
- Users familiar with UI workflow
- No migration needed

### Disadvantages of Custom Implementation

❌ **Poor Type Inference**
- All fields as "string" type
- Users must cast in Athena queries: `CAST(price AS DOUBLE)`
- Slower query performance without proper types
- More verbose SQL queries

❌ **Limited to JSON**
- Would need custom code for each new format
- No built-in support for Parquet, Avro, etc.

❌ **Maintenance Burden**
- Must handle edge cases ourselves
- Must update if data formats evolve
- Need to maintain sampling logic

❌ **Manual Partition Management**
- Partitions handled through table configuration
- Not automatically discovered from S3 structure

## Decision

**Keep Custom Implementation (Option A)**

### Rationale

1. **Cost Efficiency**: The 14x cost difference is significant, especially as the platform scales
2. **Performance**: Sub-second execution vs 1-10 minute crawler runs provides better UX
3. **Sufficient for Current Use**: JSON-only with string types meets current needs
4. **Low Complexity**: Simpler architecture with fewer AWS dependencies
5. **Proven Solution**: Already working and tested in production

### When to Reconsider

Consider migrating to AWS Glue Crawlers if:
- User complaints about type casting in queries become frequent
- Need to support non-JSON formats (Parquet, Avro, etc.)
- Automatic partition discovery becomes critical
- Cost becomes less of a concern relative to maintenance burden
- Schema evolution detection is required

### Hybrid Approach (Future Option)

If needed, we can implement both:
- **Default**: Custom inference (fast, cheap)
- **Optional**: Crawler-based inference with UI toggle and cost warning
- Let users choose: fast/cheap vs accurate types

## Implementation Details

### Current Schema Inference Code

**API Endpoint:**
- `POST /v1/organization/{organizationName}/topic/{topicName}/schema`
- Defined in: `dataspray-api-parent/dataspray-api/src/main/openapi/paths-control-topic.yaml:330-356`

**Backend Implementation:**
- `dataspray-stream-control/src/main/java/io/dataspray/stream/control/ControlResource.java:298-324`
- `dataspray-store/src/main/java/io/dataspray/store/impl/FirehoseS3AthenaBatchStore.java:470-569`

**UI Components:**
- `dataspray-site-parent/dataspray-site-dashboard/src/pages/deployment/topic.tsx:77-111`
- `dataspray-site-parent/dataspray-site-dashboard/src/deployment/TopicActionHeader.tsx`

**IAM Permissions:**
- `dataspray-package/src/main/java/io/dataspray/cdk/stream/control/ControlFunctionStack.java:216-239`
- Grants: Data Catalog (create/get database/table), Schema Registry (create/get schema/version)
- Does NOT grant: Crawler permissions

### AWS Services Used

- **AWS Glue Data Catalog** - Table and database metadata
- **AWS Glue Schema Registry** - Schema versioning with backward compatibility
- **Amazon S3** - Data storage and sampling source
- **AWS Lambda** - Execution environment for inference logic

### User Workflow

1. Navigate to Topics page in DataSpray dashboard
2. Select a topic that has batch/retention enabled
3. Click "Recalculate Schema" button in header
4. System samples S3 data and creates schema
5. Success/error alert displayed to user
6. Schema immediately available for Athena queries

## References

**AWS Documentation:**
- [AWS Glue Crawler Pricing](https://aws.amazon.com/glue/pricing/)
- [AWS Glue Data Catalog](https://docs.aws.amazon.com/glue/latest/dg/components-overview.html#data-catalog-intro)
- [AWS Glue Schema Registry](https://docs.aws.amazon.com/glue/latest/dg/schema-registry.html)

**Related Files:**
- Schema inference implementation: `dataspray-store/src/main/java/io/dataspray/store/impl/FirehoseS3AthenaBatchStore.java`
- Control API: `dataspray-stream-control/src/main/java/io/dataspray/stream/control/ControlResource.java`
- UI: `dataspray-site-parent/dataspray-site-dashboard/src/pages/deployment/topic.tsx`
- IAM: `dataspray-package/src/main/java/io/dataspray/cdk/stream/control/ControlFunctionStack.java`

## Conclusion

The custom schema inference implementation is the right choice for DataSpray at this stage. It provides:
- Excellent cost efficiency (14x cheaper)
- Fast user experience (seconds vs minutes)
- Simple architecture with minimal dependencies
- Proven production reliability

While AWS Glue Crawlers offer superior type inference and multi-format support, these benefits don't currently justify the cost and complexity increase. The decision can be revisited as platform requirements evolve.
