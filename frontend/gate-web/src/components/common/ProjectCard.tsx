import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui';

export interface Project {
  id: string;
  title: string;
  description: string;
  isPublic: boolean;
  tags?: string[];
  updatedAt?: string;
  thumbnailColor?: string;
  thumbnailUrl?: string;
}

interface ProjectCardProps {
  project: Project;
  onClick?: (project: Project) => void;
}

const THUMBNAIL_COLORS = [
  '#EFF6FF', '#F0FDF4', '#FFFBEB', '#FEF2F2',
  '#F5F2FF', '#F0F9FF', '#FFF7ED', '#F2F8FC',
];

function getColor(id: string) {
  const idx = id.charCodeAt(0) % THUMBNAIL_COLORS.length;
  return THUMBNAIL_COLORS[idx];
}

function ProjectCard({ project, onClick }: ProjectCardProps) {
  const { t } = useTranslation();
  const thumbBg = project.thumbnailColor ?? getColor(project.id);

  return (
    <article
      role="button"
      tabIndex={0}
      onClick={() => onClick?.(project)}
      onKeyDown={(e) => e.key === 'Enter' && onClick?.(project)}
      className="group flex flex-col overflow-hidden cursor-pointer outline-none
                 bg-[var(--card-bg)] border border-[var(--card-border)]
                 rounded-[var(--card-radius)] shadow-[var(--card-shadow)]
                 transition-shadow duration-[var(--transition-normal)]
                 hover:shadow-[var(--card-hover-shadow)]
                 focus-visible:ring-2 focus-visible:ring-[var(--color-border-focus)]"
    >
      {/* 썸네일 */}
      <div
        className="relative h-36 flex items-center justify-center overflow-hidden"
        style={{ backgroundColor: thumbBg }}
      >
        {project.thumbnailUrl ? (
          <img
            src={project.thumbnailUrl}
            alt={project.title}
            className="w-full h-full object-cover"
          />
        ) : (
          <span className="text-4xl font-bold opacity-20 select-none">
            {project.title.charAt(0).toUpperCase()}
          </span>
        )}
      </div>

      {/* 카드 본문 */}
      <div className="flex flex-col gap-2 p-4">
        <div className="flex items-start justify-between gap-2">
          <h3
            className="text-sm font-semibold text-[var(--color-text-primary)] line-clamp-1"
            style={{ fontSize: 'var(--text-sm)', fontWeight: 'var(--font-semibold)' }}
          >
            {project.title}
          </h3>
          <Badge color={project.isPublic ? 'success' : 'default'} dot>
            {project.isPublic ? t('project_card.public') : t('project_card.private')}
          </Badge>
        </div>

        {project.description && (
          <p
            className="text-xs text-[var(--color-text-secondary)] line-clamp-2"
            style={{ fontSize: 'var(--text-xs)', lineHeight: 'var(--leading-relaxed)' }}
          >
            {project.description}
          </p>
        )}

        {project.tags && project.tags.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-1">
            {project.tags.slice(0, 3).map((tag) => (
              <Badge key={tag} color="blue" size="sm">
                {tag}
              </Badge>
            ))}
          </div>
        )}

        {project.updatedAt && (
          <p className="text-xs text-[var(--color-text-disabled)] mt-auto pt-2 border-t border-[var(--color-border-default)]">
            {project.updatedAt}
          </p>
        )}
      </div>
    </article>
  );
}

export default ProjectCard;
