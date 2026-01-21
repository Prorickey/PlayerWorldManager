# /smart - Intelligent Problem Solving Framework

You are about to solve a complex problem using a structured, intelligent approach. Follow this framework exactly:

## Phase 1: Analysis & Planning

First, deeply analyze the problem at hand:
1. **Understand the Issue**: What exactly is broken or needs to be built?
2. **Root Cause Analysis**: Why is this happening? Trace through the code flow.
3. **Scope Definition**: What files/systems are involved?
4. **Create High-Level Plan**: Break down the solution into independent, parallelizable tasks.

Output your analysis and plan before proceeding.

## Phase 2: Script Assessment

Before implementing, assess script needs:

1. **Check Existing Scripts**: Review `scripts/` directory for reusable automation.
2. **Identify Repetitive Actions**: What tasks will you do repeatedly during this work?
3. **Anticipate Future Needs**: What scripts would make testing/verification easier?

**Create scripts proactively** if any of these apply:
- You'll run the same command 3+ times
- The operation has multiple steps that should be atomic
- Future developers would benefit from the automation
- The task involves error-prone manual steps

**Script Creation Guidelines**:
- For Gradle projects, prefer Gradle tasks over shell scripts
- Keep tasks simple and focused (one purpose each)
- Use existing tasks as building blocks (via dependsOn/finalizedBy)
- Include clear description for each task
- Test each task immediately after creating it

Launch a Sonnet agent to create any needed scripts in parallel.

## Phase 3: Skill Assessment

Review the existing skills in `.claude/skills/` and determine:
1. Do you have all the knowledge needed to implement this?
2. Are there any gaps that would significantly slow you down?

**Only if overwhelmingly needed**: Research and create new skills using parallel Opus agent calls. Each agent should:
- Research one specific topic thoroughly
- Create a well-structured skill file in `.claude/skills/`
- Include code examples and gotchas

If no new skills are needed, skip this phase entirely.

## Phase 4: Parallel Implementation

Launch parallel Sonnet agent calls for each independent task from your plan. Each agent must:
1. Implement their specific task
2. Test their changes independently (without interfering with other agents)
3. Fix any bugs they encounter
4. Report what they changed and any issues found

**Important**: Tasks must be truly independent - no two agents should modify the same file.

## Phase 5: Integration & Verification

After all agents complete:
1. Review all changes made
2. Check for any conflicts or integration issues
3. Run a full test to verify everything works together
4. Fix any remaining issues

## Phase 6: Reflection & Script/Skill Updates

Reflect on the implementation:

**Script Reflection**:
1. What commands did you run repeatedly that should have been scripts?
2. Did any manual steps cause errors that automation would prevent?
3. Are there script combinations that should be a single composite script?

**Knowledge Reflection**:
1. What went wrong or was harder than expected?
2. What knowledge was missing that would have helped?
3. Are there patterns worth documenting for future use?

**If updates needed**: Launch parallel agents to:
- Create scripts identified as missing
- Update existing skills with new learnings
- Create new skills for patterns discovered
- Document gotchas and edge cases

## Execution

Now execute this framework for the user's problem: $ARGUMENTS

Begin with Phase 1 - Analysis & Planning.
